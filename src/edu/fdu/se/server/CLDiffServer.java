package edu.fdu.se.server;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.fdu.se.base.common.Global;
import edu.fdu.se.cldiff.CLDiffCore;
import edu.fdu.se.cldiff.CLDiffAPI;
import edu.fdu.se.fileutil.FileUtil;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class CLDiffServer {
    static final String DIVIDER = "--xxx---fdse---xxx";
    static String global_Path;

    public static void main(String[] arg) throws Exception {
        global_Path = arg[0];
        Global.globalPath = arg[0];
        HttpServer server = HttpServer.create(new InetSocketAddress(12007), 0);
        server.createContext("/DiffMiner/main/genCache", new CacheGeneratorHandler());
        server.createContext("/DiffMiner/main/fetchMetaCache", new FetchMetaCacheHandler());
        server.createContext("/DiffMiner/main/fetchContent", new FetchFileContentHandler());
        server.createContext("/DiffMiner/main/clearCache",new ClearCacheHandler());
        server.start();

        //test
//        Meta meta = readFromMeta(global_Path+"spring-framework/3c1adf7f6af0dff9bda74f40dabe8cf428a62003/meta");
//        CLDiffAPI diff = new CLDiffAPI(global_Path,meta);
//        diff.generateDiffMinerOutput();
    }

    /**
     * invoked from test
     * @param path
     * @return
     */
    static Meta readFromMeta(String path){
        try {
            FileInputStream fis = new FileInputStream(path);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            String line;
            StringBuffer sb = new StringBuffer();
            while((line = br.readLine())!= null){
                sb.append(line);
            }
            Meta obj = new Gson().fromJson(sb.toString(), Meta.class);
            return obj;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;

    }

    /**
     * 获取文件内容 link diff
     */
    static class FetchFileContentHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("FetchFileContentHandler");
            InputStream is = exchange.getRequestBody();
            byte[] cache = new byte[100];
            int res;
            StringBuilder postString = new StringBuilder();
            while ((res = is.read(cache)) != -1) {
                String a = new String(cache).substring(0, res);
                postString.append(a);
            }
            System.out.println(postString);
            String[] entries = postString.toString().split("&");
            Map<String,String> mMap = new HashMap<>();

            for(String entry: entries){
                String[] kvs = entry.split("=");
                mMap.put(kvs[0],kvs[1]);
            }
            // author、commit_hash、parent_commit_hash、project_name、prev_file_path、curr_file_path
            String author = "";

            String commit_hash = mMap.get("commit_hash");
            String parent_commit_hash = mMap.get("parent_commit_hash");
            String project_name = mMap.get("project_name");
            String fileName = mMap.get("file_name");
            String[] fileNames = fileName.split("---");
            int id = Integer.valueOf(fileNames[0]);
            //文件路径为global_Path/project_name/commit_id/meta.json
            String metaStr = FileUtil.read(global_Path + project_name + "/" + commit_hash + "/meta.json");
            Meta meta = new Gson().fromJson(metaStr, Meta.class);
            CommitFile file = meta.getFiles().get(id);
            String action = meta.getActions().get(id);
            String curr_file_path = "";
            String prev_file_path = "";
            String currFileContent = "";
            String prevFileContent = "";
            String diff = null;
            if("modified".equals(action)){
                prev_file_path = file.getPrev_file_path();
                curr_file_path = file.getCurr_file_path();
                currFileContent = FileUtil.read(global_Path + project_name + "/" + commit_hash + "/" + curr_file_path);
                prevFileContent = FileUtil.read(global_Path + project_name + "/" + commit_hash + "/" + prev_file_path);
                if(!CLDiffCore.isFilter(prev_file_path)){
                    List<CommitFile> commitFileList = meta.getFiles();
                    String diffPath = "";
                    for (CommitFile commitFile : commitFileList) {
                        if (commitFile.getCurr_file_path().equals(curr_file_path)) {
                            diffPath = commitFile.getDiffPath();
                            break;
                        }
                    }
                    diff = FileUtil.read(diffPath);
                }
            }else if("added".equals(action)){
                curr_file_path = file.getCurr_file_path();
                currFileContent = FileUtil.read(global_Path + project_name + "/" + commit_hash + "/" + curr_file_path);
            }else if("deleted".equals(action)){
                prev_file_path = file.getPrev_file_path();
                prevFileContent = FileUtil.read(global_Path + project_name + "/" + commit_hash + "/" + prev_file_path);
            }
            String link = FileUtil.read(meta.getLinkPath());
            Content content = new Content(prevFileContent, currFileContent, diff, link);
            String contentResultStr = new Gson().toJson(content);
//            System.out.println(contentResultStr);
//            System.out.println(String.valueOf(contentResultStr.length()));
            byte[] bytes = contentResultStr.getBytes();
            exchange.sendResponseHeaders(201, bytes.length);
            try (BufferedOutputStream out = new BufferedOutputStream(exchange.getResponseBody())) {
                try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
                    byte [] buffer = new byte [1000];
                    int count ;
                    while ((count = bis.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }
                    out.close();
                }
            }
        }
    }

    static class ClearCacheHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("clear cache");
            Runtime runtime = Runtime.getRuntime();
//            String[] args = new String[] {"rm -rf", "/c", String.format("rm -rf %s", global_Path)};
            runtime.exec("rm -rf " + global_Path);
            OutputStream os = exchange.getResponseBody();
            String success = "SUCCESS\n";
            exchange.sendResponseHeaders(200,success.length());
            os.write(success.getBytes());
            os.close();
        }
    }

    /**
     * 获取Meta缓存
     */
    static class FetchMetaCacheHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("FetchMetaCacheHandler");
            InputStream is = exchange.getRequestBody();
            OutputStream os = exchange.getResponseBody();
            byte[] cache = new byte[100];
            int res;
            StringBuilder postString = new StringBuilder();
            while ((res = is.read(cache)) != -1) {
                String a = new String(cache).substring(0, res);
                postString.append(a);
                // postString += (new String(cache)).substring(0, res);
            }
            System.out.println(postString);
            //获得commit_hash
            String commitHash = new JSONObject(postString.toString()).getString("commit_hash");
            String projectName = new JSONObject(postString.toString()).getString("project_name");
            //读取文件
            //文件路径为global_Path/project_name/commit_id/meta.txt
            String meta = FileUtil.read(global_Path + projectName + "/" + commitHash + "/meta.json");
            System.out.println(meta);
            exchange.sendResponseHeaders(200, meta.length());
            os.write(meta.getBytes());
            os.close();
        }
    }

    static class CacheGeneratorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("CacheHandler");
            InputStream is = exchange.getRequestBody();
            byte[] cache = new byte[1000 * 1024];
            int res;
            StringBuilder postString = new StringBuilder();
            while ((res = is.read(cache)) != -1) {
                //todo
                //这里字符串拼接最好改成StringBuilder拼接，在循环里做str+str操作可能会有内存问题
                String a = new String(cache).substring(0, res);
                postString.append(a);
                // postString += (new String(cache)).substring(0, res);
            }
            System.out.println(postString);

            //保存为文件
            String[] data = postString.toString().split(DIVIDER);
            if (data.length <= 1) {
                return;
            }
            int size = data.length;
            //找到meta信息
            Meta meta = FileUtil.filterMeta(data[size - 2]);
            //建立一个文件夹
            //文件夹命名为commit_hash
            //文件名以name字段的hash值
            File folder = FileUtil.createFolder(global_Path + meta.getProject_name() + "/" + meta.getCommit_hash());
            //代码文件
            FileUtil.convertCodeToFile(data, folder, meta);
            CLDiffAPI diff = new CLDiffAPI(global_Path, meta);
            diff.generateDiffMinerOutput();
            List<String> filePathList = Global.outputFilePathList;
            //diff
            int diffFileSize = filePathList.size() - 1;
            for (int i = 0; i < diffFileSize; i++) {
                String diffPath = filePathList.get(i);
                meta.getFiles().get(i).setDiffPath(diffPath);
            }
            //link
            meta.setLinkPath(filePathList.get(diffFileSize));
            //写入meta文件
            FileUtil.createFile("meta.json", new GsonBuilder().setPrettyPrinting().create().toJson(meta), folder);
            String response = new Gson().toJson(meta);
            System.out.println(response);
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

    }
}
