/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.controller.nacos;

import com.alibaba.csp.sentinel.dashboard.auth.AuthAction;
import com.alibaba.csp.sentinel.dashboard.auth.AuthService.PrivilegeType;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.SystemRuleEntity;
import com.alibaba.csp.sentinel.dashboard.domain.Result;
import com.alibaba.csp.sentinel.dashboard.repository.rule.RuleRepository;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * for system rules
 * @author xuanzhui
 */
@RestController
@RequestMapping("/nacos/system")
public class SystemNacosController {

    private final Logger logger = LoggerFactory.getLogger(SystemNacosController.class);

    @Autowired
    private RuleRepository<SystemRuleEntity, Long> repository;
    @Autowired
    @Qualifier("systemRuleNacosProvider")
    private DynamicRuleProvider<List<SystemRuleEntity>> ruleProvider;
    @Autowired
    @Qualifier("systemRuleNacosPublisher")
    private DynamicRulePublisher<List<SystemRuleEntity>> rulePublisher;

    @GetMapping("/rules")
    @AuthAction(PrivilegeType.READ_RULE)
    public Result<List<SystemRuleEntity>> apiQueryMachineRules(@RequestParam String app) {
        logger.info("query app {} system rules", app);

        if (StringUtil.isEmpty(app)) {
            return Result.ofFail(-1, "app can't be null or empty");
        }

        try {
            List<SystemRuleEntity> rules = ruleProvider.getRules(app);
            if (rules != null && !rules.isEmpty()) {
                for (SystemRuleEntity entity : rules) {
                    entity.setApp(app);
                }
            }
            rules = repository.saveAll(rules);
            return Result.ofSuccess(rules);
        } catch (Throwable throwable) {
            logger.error("Error when querying flow rules", throwable);
            return Result.ofThrowable(-1, throwable);
        }
    }

    private int countNotNullAndNotNegative(Number... values) {
        int notNullCount = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && values[i].doubleValue() >= 0) {
                notNullCount++;
            }
        }
        return notNullCount;
    }

    @PostMapping("/rule")
    @AuthAction(PrivilegeType.WRITE_RULE)
    public Result<SystemRuleEntity> apiAdd(@RequestBody SystemRuleEntity entity) {
        logger.info("adding new system rule: {}", JSON.toJSONString(entity));

        if (StringUtil.isEmpty(entity.getApp())) {
            return Result.ofFail(-1, "app can't be null or empty");
        }

        int notNullCount = countNotNullAndNotNegative(entity.getHighestSystemLoad(), entity.getAvgRt(),
                entity.getMaxThread(), entity.getQps(), entity.getHighestCpuUsage());
        if (notNullCount != 1) {
            return Result.ofFail(-1, "only one of [highestSystemLoad, avgRt, maxThread, qps,highestCpuUsage] "
                + "value must be set > 0, but " + notNullCount + " values get");
        }
        if (null != entity.getHighestCpuUsage() && entity.getHighestCpuUsage() > 1) {
            return Result.ofFail(-1, "highestCpuUsage must between [0.0, 1.0]");
        }

        entity.setApp(entity.getApp().trim());
        // -1 is a fake value
        if (null == entity.getHighestSystemLoad()) {
            entity.setHighestSystemLoad(-1D);
        }

        if (null == entity.getHighestCpuUsage()) {
            entity.setHighestCpuUsage(-1D);
        }

        if (entity.getAvgRt() == null) {
            entity.setAvgRt(-1L);
        }
        if (entity.getMaxThread() == null) {
            entity.setMaxThread(-1L);
        }
        if (entity.getQps() == null) {
            entity.setQps(-1D);
        }
        Date date = new Date();
        entity.setGmtCreate(date);
        entity.setGmtModified(date);
        try {
            entity = repository.save(entity);
            publishRules(entity.getApp());
        } catch (Throwable throwable) {
            logger.error("Add SystemRule error", throwable);
            return Result.ofThrowable(-1, throwable);
        }

        return Result.ofSuccess(entity);
    }

    @PutMapping("/rule/{id}")
    @AuthAction(PrivilegeType.WRITE_RULE)
    public Result<SystemRuleEntity> apiUpdateIfNotNull(@PathVariable("id") Long id,
                                                       @RequestBody SystemRuleEntity entity) {
        logger.info("updating system rule, id {}, new rule {}", id, entity);

        if (id == null) {
            return Result.ofFail(-1, "id can't be null");
        }
        SystemRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofFail(-1, "id " + id + " dose not exist");
        }

        entity.setApp(oldEntity.getApp().trim());

        if (entity.getHighestSystemLoad() != null) {
            if (entity.getHighestSystemLoad() < 0) {
                return Result.ofFail(-1, "highestSystemLoad must >= 0");
            }
        } else {
            entity.setHighestSystemLoad(-1D);
        }

        if (entity.getHighestCpuUsage() != null) {
            if (entity.getHighestCpuUsage() < 0) {
                return Result.ofFail(-1, "highestCpuUsage must >= 0");
            }
            if (entity.getHighestCpuUsage() > 1) {
                return Result.ofFail(-1, "highestCpuUsage must <= 1");
            }
        } else {
            entity.setHighestCpuUsage(-1D);
        }

        if (entity.getAvgRt() != null) {
            if (entity.getAvgRt() < 0) {
                return Result.ofFail(-1, "avgRt must >= 0");
            }
        } else {
            entity.setAvgRt(-1L);
        }

        if (entity.getMaxThread() != null) {
            if (entity.getMaxThread() < 0) {
                return Result.ofFail(-1, "maxThread must >= 0");
            }
        } else {
            entity.setMaxThread(-1L);
        }

        if (entity.getQps() != null) {
            if (entity.getQps() < 0) {
                return Result.ofFail(-1, "qps must >= 0");
            }
        } else {
            entity.setQps(-1D);
        }

        entity.setId(oldEntity.getId());
        entity.setGmtCreate(oldEntity.getGmtCreate());
        Date date = new Date();
        entity.setGmtModified(date);

        try {
            entity = repository.save(entity);
            publishRules(entity.getApp());
        } catch (Throwable throwable) {
            logger.error("save error:", throwable);
            return Result.ofThrowable(-1, throwable);
        }

        return Result.ofSuccess(entity);
    }

    @DeleteMapping("/rule/{id}")
    @AuthAction(PrivilegeType.DELETE_RULE)
    public Result<Long> delete(@PathVariable("id") Long id) {
        logger.info("removing system rule, id {}", id);
        if (id == null) {
            return Result.ofFail(-1, "id can't be null");
        }

        SystemRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofSuccess(null);
        }

        try {
            repository.delete(id);
            publishRules(oldEntity.getApp());
        } catch (Throwable throwable) {
            logger.error("delete error:", throwable);
            return Result.ofThrowable(-1, throwable);
        }

        return Result.ofSuccess(id);
    }

    private void publishRules(String app) throws Exception {
        List<SystemRuleEntity> rules = repository.findAllByApp(app);
        rulePublisher.publish(app, rules);
    }
}
