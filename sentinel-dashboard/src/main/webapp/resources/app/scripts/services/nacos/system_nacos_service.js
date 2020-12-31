var app = angular.module('sentinelDashboardApp');

app.service('SystemNacosService', ['$http', function ($http) {
    this.queryMachineRules = function (app, ip, port) {
        var param = {
            app: app,
            ip: ip,
            port: port
        };
        return $http({
            url: '/nacos/system/rules',
            params: param,
            method: 'GET'
        });
    };

    this.newRule = function (rule) {
        var param = {
            app: rule.app,
            ip: rule.ip,
            port: rule.port
        };
        if (rule.grade == 0) {// avgLoad
            param.highestSystemLoad = rule.highestSystemLoad;
        } else if (rule.grade == 1) {// avgRt
            param.avgRt = rule.avgRt;
        } else if (rule.grade == 2) {// maxThread
            param.maxThread = rule.maxThread;
        } else if (rule.grade == 3) {// qps
            param.qps = rule.qps;
        } else if (rule.grade == 4) {// cpu
            param.highestCpuUsage = rule.highestCpuUsage;
        }

        return $http({
            url: '/nacos/system/rule',
            data: param,
            method: 'POST'
        });
    };

    this.saveRule = function (rule) {
        var param = {
            id: rule.id,
        };
        if (rule.grade == 0) {// avgLoad
            param.highestSystemLoad = rule.highestSystemLoad;
        } else if (rule.grade == 1) {// avgRt
            param.avgRt = rule.avgRt;
        } else if (rule.grade == 2) {// maxThread
            param.maxThread = rule.maxThread;
        } else if (rule.grade == 3) {// qps
            param.qps = rule.qps;
        } else if (rule.grade == 4) {// cpu
            param.highestCpuUsage = rule.highestCpuUsage;
        }

        return $http({
            url: '/nacos/system/rule/' + rule.id,
            data: param,
            method: 'PUT'
        });
    };

    this.deleteRule = function (rule) {
        return $http({
            url: '/nacos/system/rule/' + rule.id,
            method: 'DELETE'
        });
    };
}]);
