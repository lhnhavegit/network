package com.lhn.network.task;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.StaticLog;
import com.lhn.network.utils.HttpClientUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;


import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
@EnableScheduling
public class NetworkTask {

    private ConcurrentHashMap<String, String> map =new ConcurrentHashMap<>();

    @Value("${network.try_num:3}")
    private Integer time;

    @Value("#{'${network.phones:}'.empty ? null : '${network.phones:}'.split(',')}")
    private List<String> phones;

    @Value("#{'${network.ips:}'.empty ? null : '${network.ips:}'.split(',')}")
    private List<String> ipConfigs;

    @Value("${network.park_name:汇九川智慧停车场}")
    private String parkName;

    @Value("${network.url:http://t.cms.hjcpay.com//sms/sendSms}")
    private String url;

    @Value("${network.warning:SMS_191817743}")
    private String warning;

    @Value("${network.restore:SMS_217419368}")
    private String restore;

    @Scheduled(fixedRateString = "${network.fix_time:5000}")
    private void network() {
        StaticLog.info("***********************************************");
        List<String> ips = getIps();
        StaticLog.info("当前电脑具有的ip:{}",ips);
        StaticLog.info("当前系统配置ip:{}",ipConfigs);
        StaticLog.info("当前使用网卡:{}",NetUtil.getLocalhost().getHostAddress());
        Optional.ofNullable(ipConfigs).ifPresent(ipConfigs->ipConfigs.forEach(
            ip->{
                boolean flag = false;
                if(ips.contains(ip)){
                    flag = exeCmd(ip);
                }
                if(flag){
                    StaticLog.info("网卡：{},网络正常",ip);
                }else {
                    if(!map.containsKey(ip)){
                        StaticLog.error("网卡：{},网略故障",ip);
                        boolean tryFlag = tryAgain(ip);
                        if(tryFlag){
                            StaticLog.info("网卡：{},网络恢复正常",ip);
                        }else {
                            map.put(ip,String.valueOf(System.currentTimeMillis()));
                            Optional.ofNullable(phones).ifPresent(phone->{
                                JSONObject obj = JSONUtil.createObj().putOpt("mobiles",phones).putOpt("smsId",warning)
                                        .putOpt("time",DateUtil.now()).putOpt("parkName",parkName);
                                String s = HttpClientUtils.sendHttpPostJson(url, JSONUtil.toJsonStr(obj));
                                StaticLog.info("已经发送短信给：{},通知网卡：{} 断网 ",phone,ip);
                            });
                        }
                    }else{
                        StaticLog.error("网卡：{},网略故障,   故障发生时间：{}",ip,DateUtil.date(Long.parseLong(map.get(ip))));
                    }
                }
                StaticLog.info("执行时间：{}",DateUtil.now());
            }));
    }

    private  boolean exeCmd(String ip) {

        StringJoiner stringJoiner = new StringJoiner(" ");
        stringJoiner.add("ping -S").add(ip).add("www.baidu.com");
        String string = RuntimeUtil.execForStr(Charset.forName("GBK"), stringJoiner.toString());
        boolean flag = string.indexOf("TTL")>0;
        if(flag && map.containsKey(ip)){
            StaticLog.info("网卡:{} 恢复网络连接!",ip);
            StaticLog.info("已经发送短信通知管理员，ip:{} 恢复网络",ip);
            map.remove(ip);
            JSONObject obj = JSONUtil.createObj().putOpt("mobiles",phones).putOpt("smsId",restore)
                    .putOpt("time",DateUtil.now()).putOpt("parkName",parkName);
            String s = HttpClientUtils.sendHttpPostJson(url, JSONUtil.toJsonStr(obj));
        }
        return flag;

    }

    private List<String> getIps(){
        LinkedHashSet<InetAddress> inetAddresses = NetUtil.localAddressList((address) -> {
            return !address.isLoopbackAddress() && address instanceof Inet4Address;
        });
        return inetAddresses.stream().map(InetAddress::getHostAddress).filter(ip -> ip.contains("192.168.")).collect(Collectors.toList());
    }

    private boolean tryAgain(String ip){
        boolean flag = false;
        for (int i = 0; i < time; i++) {
            flag = exeCmd(ip);
            StaticLog.warn("第{}次重试结果:{}",i+1,flag);
            if(flag){
                break;
            }
        }
        return flag;
    }
}
