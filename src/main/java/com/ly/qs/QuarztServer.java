package com.ly.qs;

import com.quzheng.service.dao.mapper.QzUrlMapper;
import com.quzheng.service.dao.model.QzUrl;
import com.quzheng.service.impl.RunLog;
import com.quzheng.service.impl.specialQz.SpecialService;

import javax.annotation.Resource;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 定时定向取证
 * Created by liu.yang on 2016/7/26 0026.
 */
public class QuarztServer {

    //初始化url.file文件数据
    public static List<String> cacheData = Collections.synchronizedList(new ArrayList());

    //缓存数据大小
    public static int limitNum = 10000;

    @Resource
    QzUrlMapper qzUrlMapper;
    @Resource
    SpecialService specialService;

    public void init() throws IOException {
        List<String> strings = readText();
        int size = strings.size();
        for(int i=0;i<size;i++){
            cacheData.add(strings.get(i));
        }
        RunLog.logDao.info("缓存处理OK! 定时定向取证数据"+cacheData.size()+"条");

    }

    public void flushCache(String url){
        File file = getFile();
        removeContent(file,url);
        for(int i=cacheData.size()-1;i>-1;i--){
            if(url.equals(cacheData.get(i))){
                cacheData.remove(i);
//                removeContent(file,cacheData.get(i));
                RunLog.logDao.info("缓存处理OK! 已从缓存中清楚1条数据");
            }
        }
    }

    //新增缓存数据
    public void addDataToCache(List<String> urls){
        for(int i=0;i<urls.size();i++){
            cacheData.add(urls.get(i));
        }
        File file = getFile();
        appendContent(file,urls,true);
        if(cacheData.size()>limitNum){
            cacheData.remove(0);
        }
    }

    public void work() {
        int i=0;
        for(String url:cacheData){
            QzUrl qzUrl = new QzUrl();
            qzUrl.setUrl_id(System.currentTimeMillis()+""+i);
            qzUrl.setUrl_addr(url);
            qzUrlMapper.insert(qzUrl);
            i++;
        }
        RunLog.logDao.info("定时定向取证"+i+"条");
    }

    private List<String>  readText(){
        File file = getFile();

        List<String> strings = new ArrayList<>();
        BufferedReader reader = null;
        try {
            System.out.println("以行为单位读取文件内容，一次读一整行：");
            reader = new BufferedReader(new FileReader(file));
            String tempString = null;
            int line = 1;
            // 一次读入一行，直到读入null为文件结束
            while ((tempString = reader.readLine()) != null) {
                // 显示行号
                System.out.println("line " + line + ": " + tempString);
                line++;
                strings.add(tempString);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
        return strings;
    }

    private File getFile(){
        String fileAddress = getClass().getClassLoader().getResource("url.text").toString().replace("file:/","");
        return new File(fileAddress);
    }

    /**
     * 追加：BufferedWriter
     */
    private void appendContent(File file, List<String> content,boolean falg) {
        try {

            BufferedWriter writer = new BufferedWriter(new FileWriter(file, falg));            // 文件长度，字节数
            for(int i=0;i<content.size();i++){
                String string = content.get(i);
                writer.newLine();
                writer.write(string);
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void removeContent(File file, String content){
        List<String> strings = readText();

        for(int i=0;i<strings.size();i++){
            String string = strings.get(i);
            if(content.equals(string)){
                strings.remove(string);
            }
        }

        appendContent(file,strings,false);
    }

//    public static void main(String[] args){
//        QuarztServer specialServer = new QuarztServer();
//        List<String> strings = new ArrayList<>();
//        strings.add("http://www.cnblogs.com/lostyue/archive/2011/06/27/2091686.html");
//        specialServer.flushCache("http://www.cnblogs.com/lostyue/archive/2011/06/27/2091686.html");
//    }



}
