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

            String homeUrl = "https://www-hj.douyin.com/aweme/v1/web/aweme/post/"
                            + "?device_platform=webapp"
                            + "&aid=6383"
                            + "&channel=channel_pc_web"
                            + "&sec_user_id=" + SEC_USER_ID
                            + "&max_cursor=" + maxCursor
                            + "&count=18"
                            + "&publish_video_strategy_type=2";

            System.out.println("请求 max_cursor = " + maxCursor);

            String homeJson = httpGet(homeUrl);
            JsonNode root = MAPPER.readTree(homeJson);

            System.out.println("本页作品数：" + root.path("aweme_list").size());
            System.out.println("has_more：" + root.path("has_more").asInt());
            System.out.println("max_cursor：" + root.path("max_cursor").asLong());

            JsonNode awemeList = root.path("aweme_list");
            if (!awemeList.isArray() || awemeList.size() == 0) {
                throw new RuntimeException("aweme_list 为空");
            }

            int total = awemeList.size();

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

                String baseFileName = date + " " + title;

                // 防止特殊字符
                baseFileName = baseFileName.replaceAll("[\\\\/:*?\"<>|]", "_");

                //判断图文类型
                int isMultiContent = aweme.path("is_multi_content").asInt();

                if (isMultiContent == 1) {

                    JsonNode images = aweme.path("images");

                    System.out.println("[图文作品,共 " + images.size() + " 条]");

                    for (int index = 0; index < images.size(); index++) {

                        JsonNode image = images.get(index);

                        int fileIndex = index + 1;

                        int livePhotoType = image.path("live_photo_type").asInt();

                        if (livePhotoType == 1) {

                            System.out.println("\n图文作品第 " + fileIndex + " 条，为视频");

                            String videoUrl = null;

                            JsonNode urlList =
                                    image.path("video")
                                            .path("play_addr")
                                            .path("url_list");

                            for (JsonNode urlNode : urlList) {

                                String url = urlNode.asText();

                                if (url.contains("aweme/v1/play")) {

                                    videoUrl = url;

                                    break;
                                }
                            }

                            if (videoUrl == null) {
                                continue;
                            }

                            String savePath =
                                    authorDir.getAbsolutePath()
                                            + File.separator
                                            + baseFileName + fileIndex + ".mp4";

                            System.out.println(videoUrl);
                            System.out.println(savePath);

                            download(videoUrl, savePath);

                            System.out.println("[图文-视频 下载完成]");
                        }

                        else {

                            System.out.println("\n图文作品第 " + fileIndex + " 条，为图片");

                            JsonNode urlList =
                                    image.path("download_url_list");

                            if (urlList.size() == 0) {
                                continue;
                            }

                            String imageUrl = urlList.get(2).asText();

                            String savePath =
                                    authorDir.getAbsolutePath()
                                            + File.separator
                                            + baseFileName + fileIndex + ".jpg";

                            System.out.println(imageUrl);
                            System.out.println(savePath);

                            download(imageUrl, savePath);

                            System.out.println("[图文-图片 下载完成]");
                        }

                    }
                }
                else{

                    System.out.println("[普通视频]");

                    int maxWidth = -1;

                    JsonNode bestPlayAddr = null;

                    JsonNode videoNode = aweme.path("video");

                    JsonNode bitRateList = videoNode.path("bit_rate");

                    for (JsonNode bitRate : bitRateList) {

                        JsonNode playAddr = bitRate.path("play_addr");

                        int width = playAddr.path("width").asInt();

                        if (width > maxWidth) {
                            maxWidth = width;
                            bestPlayAddr = playAddr;
                        }
                    }

                    if (bestPlayAddr != null) {

                        JsonNode urlList = bestPlayAddr.path("url_list");

                        String videoUrl = null;

                        for (JsonNode urlNode : urlList) {

                            String url = urlNode.asText();

                            if (url.contains("aweme/v1/play")) {

                                videoUrl = url;

                                break;
                            }
                        }

                        if (videoUrl == null) {
                            System.out.println("未找到视频地址");
                            continue;
                        }

                        String savePath =
                                authorDir.getAbsolutePath()
                                        + File.separator
                                        + baseFileName + ".mp4";

                        System.out.println(videoUrl);
                        System.out.println(savePath);

                        download(videoUrl, savePath);

                        System.out.println("[视频-视频下载完成]");
                    }
                }

            }

            int hasMore = root.path("has_more").asInt();

            System.out.println("has_more = " + hasMore);

            if (hasMore == 0) {
                break;
            }

            maxCursor = root.path("max_cursor").asLong();

            System.out.println("下一页 max_cursor = " + maxCursor);

        }

        System.out.println("\n---作品总数量为" + totalall + "---");

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

//        System.out.println("【测试用，下载方法已注释】");
    }

}