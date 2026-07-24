package com.sola.pickstars;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

public class PickStarsApplication {

    static final ObjectMapper MAPPER = new ObjectMapper();

    // ===== 从配置读取 =====
    static String COOKIE;
    static String SEC_USER_ID;
    static String DOWNLOAD_DIR;

    public static void main(String[] args) throws Exception {
        System.out.println("====== Douyin Downloader Start ======");

        loadConfig();

        long maxCursor = 0;

        long totalall = 0;

        while (true) {

            String homeUrl =
                    "https://www-hj.douyin.com/aweme/v1/web/aweme/post/"
                            + "?device_platform=webapp"
                            + "&aid=6383"
                            + "&channel=channel_pc_web"
                            + "&sec_user_id=" + SEC_USER_ID
                            + "&max_cursor=" + maxCursor
                            + "&count=18"
                            + "&publish_video_strategy_type=2";

            System.out.println("\n==============================");
            System.out.println("请求 max_cursor = " + maxCursor);

            String homeJson = httpGet(homeUrl);
            JsonNode root = MAPPER.readTree(homeJson);

            System.out.println("作品数：" + root.path("aweme_list").size());
            System.out.println("has_more：" + root.path("has_more").asInt());
            System.out.println("max_cursor：" + root.path("max_cursor").asLong());

            JsonNode awemeList = root.path("aweme_list");
            if (!awemeList.isArray() || awemeList.size() == 0) {
                throw new RuntimeException("aweme_list 为空");
            }

            int total = awemeList.size();

            System.out.println("================================");
            System.out.println("本次准备下载 " + total + " 个作品");
            System.out.println("================================");

            totalall += total;


            File dir = new File(DOWNLOAD_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }


            for (int i = 0; i < awemeList.size(); i++) {

                JsonNode aweme = awemeList.get(i);

                String awemeId = aweme.path("aweme_id").asText();

                System.out.println("\n========== 第 " + (i + 1) + " 个作品 ==========");
                System.out.println("awemeId = " + awemeId);


                String nickname = aweme
                        .path("author")
                        .path("nickname")
                        .asText("unknown");


                File authorDir = new File(
                        DOWNLOAD_DIR + nickname + "/"
                );

                if (!authorDir.exists()) {
                    authorDir.mkdirs();
                }


                long createTime = aweme.path("create_time").asLong();


                String date = new java.text.SimpleDateFormat("yyyy-MM-dd")
                        .format(new java.util.Date(createTime * 1000));


                String title  = aweme.path("desc")
                        .asText("no_title");

                if (title.contains("#")) {
                    title = title.substring(0, title.indexOf("#"));
                }

                title = title.trim();



                String fileName = date + " " + title + ".mp4";


                // 防止特殊字符
                fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");


                String videoUrl = null;


                // ==================================================
                // 1. 优先检查 aweme.video
                // ==================================================

                JsonNode videoNode =
                        aweme.path("video");


                JsonNode urlList =
                        videoNode
                                .path("play_addr")
                                .path("url_list");



                if (urlList.isArray()) {

                    for (JsonNode urlNode : urlList) {

                        String url = urlNode.asText();

                        if (url.contains("aweme/v1/play")) {

                            videoUrl = url;

                            System.out.println("[类型] 普通视频");
                            System.out.println(videoUrl);

                            break;
                        }
                    }

                }



                if (videoUrl == null) {

                    JsonNode images = aweme.path("images");

                    if (images.isArray()) {
                        for (JsonNode image : images) {

                            JsonNode imageUrlList =
                                    image
                                            .path("video")
                                            .path("play_addr")
                                            .path("url_list");

                            if (imageUrlList.isArray()) {

                                for (JsonNode urlNode : imageUrlList) {

                                    String url = urlNode.asText();

                                    if (url.contains("aweme/v1/play")) {

                                        videoUrl = url;

                                        System.out.println("[类型] 图文视频");
                                        System.out.println(videoUrl);

                                        break;
                                    }
                                }
                            }


                            if (videoUrl != null) {
                                break;
                            }

                        }

                    }

                }


                // ==================================================
                // 下载
                // ==================================================

                if (videoUrl == null) {

                    System.out.println("[SKIP] 没找到视频地址");

                    continue;
                }


                String savePath =
                        authorDir.getAbsolutePath()
                                + File.separator
                                + fileName;


                System.out.println("[下载]");
                System.out.println(savePath);


//                download(videoUrl, savePath);


                System.out.println("[完成]");
            }

            int hasMore = root.path("has_more").asInt();

            System.out.println("has_more = " + hasMore);

            if (hasMore == 0) {
                break;
            }

            maxCursor = root.path("max_cursor").asLong();

            System.out.println("下一页 max_cursor = " + maxCursor);


        }

        System.out.println("总数量为"+totalall);

    }

    /* ================= 配置加载 ================= */
    static void loadConfig() throws IOException {
        Properties p = new Properties();
        try (InputStream in = PickStarsApplication.class
                .getClassLoader()
                .getResourceAsStream("application-local.properties")) {

            if (in == null) {
                throw new RuntimeException("找不到properties");
            }
            p.load(in);
        }

        COOKIE = p.getProperty("douyin.cookie");
        SEC_USER_ID = p.getProperty("douyin.secUserId");
        DOWNLOAD_DIR = p.getProperty("douyin.downloadDir");

        System.out.println("[CONFIG] secUserId = " + SEC_USER_ID);
        System.out.println("[CONFIG] downloadDir = " + DOWNLOAD_DIR);
    }

    /* ================= HTTP 工具 ================= */
    public static String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Referer", "https://www.douyin.com/");
        conn.setRequestProperty("Cookie", COOKIE);

        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes());
        }
    }

    public static void download(String urlStr, String filePath) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Referer", "https://www.douyin.com/");
        conn.setRequestProperty("Cookie", COOKIE);

        try (InputStream in = conn.getInputStream();
             OutputStream out = new FileOutputStream(filePath)) {
            in.transferTo(out);
        }
    }

}
