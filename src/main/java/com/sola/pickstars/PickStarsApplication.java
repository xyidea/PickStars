package com.sola.pickstars;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sola.pickstars.douyindl.DownloadItem;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class PickStarsApplication {

    static final ObjectMapper MAPPER = new ObjectMapper();

    // ===== 从配置读取 =====
    static String COOKIE;
    static String SEC_USER_ID;
    static String DOWNLOAD_DIR;

    public static void main(String[] args) throws Exception {
        System.out.println("====== Douyin Downloader Start ======");

        List<DownloadItem> downloadItems= new ArrayList<>();

        loadConfig();

        //用来判断是否有下一页
        long maxCursor = 0;

        //计算作品数量
        long awemeCount = 0;

        //下载重试maxRound轮
        int round = 1;
        int maxRound = 5;

        //记录第几页，其实是网络请求的页数
        int page = 1;

        //记录下载成功的数量和文件总数
        int successCount = 0;
        int totalDownloadCount;

        while (true) {

            String homeUrl = "https://www-hj.douyin.com/aweme/v1/web/aweme/post/"
                            + "?device_platform=webapp"
                            + "&aid=6383"
                            + "&channel=channel_pc_web"
                            + "&sec_user_id=" + SEC_USER_ID
                            + "&max_cursor=" + maxCursor
                            + "&count=18"
                            + "&publish_video_strategy_type=2";

            String homeJson = httpGet(homeUrl);
            JsonNode root = MAPPER.readTree(homeJson);

            JsonNode awemeList = root.path("aweme_list");
            if (!awemeList.isArray() || awemeList.size() == 0) {
                throw new RuntimeException("aweme_list 为空");
            }

            int total = awemeList.size();

            awemeCount += total;


            File dir = new File(DOWNLOAD_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }


            for (int i = 0; i < awemeList.size(); i++) {

                JsonNode aweme = awemeList.get(i);

                String awemeId = aweme.path("aweme_id").asText();

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
                baseFileName = baseFileName.replaceAll("[\\\\/:*?\"<>|。]", "");

                //判断图文类型
                int isMultiContent = aweme.path("is_multi_content").asInt();

                if (isMultiContent == 1) {

                    JsonNode images = aweme.path("images");

                    for (int index = 0; index < images.size(); index++) {

                        JsonNode image = images.get(index);

                        int fileIndex = index + 1;

                        int livePhotoType = image.path("live_photo_type").asInt();

                        if (livePhotoType == 1) {

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
                                            + baseFileName
                                            + "_"
                                            + fileIndex
                                            + ".mp4";

                            downloadItems.add(new DownloadItem(2,awemeId,videoUrl, savePath));
                        }

                        else {

                            JsonNode urlList =
                                    image.path("download_url_list");

                            if (urlList.size() == 0) {
                                continue;
                            }

                            String imageUrl = urlList.get(2).asText();

                            String savePath =
                                    authorDir.getAbsolutePath()
                                            + File.separator
                                            + baseFileName
                                            + "_"
                                            + fileIndex
                                            + ".jpg";

                            downloadItems.add(new DownloadItem(3,awemeId,imageUrl, savePath));
                        }

                    }
                }
                else{

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

                        downloadItems.add(new DownloadItem(1,awemeId,videoUrl, savePath));
                    }
                }

            }

            System.out.println("\n第" + page + "页解析完成，本页作品数：" + root.path("aweme_list").size());
            page++;

            int hasMore = root.path("has_more").asInt();

            if (hasMore == 0) {
                break;
            }

            maxCursor = root.path("max_cursor").asLong();

        }

        totalDownloadCount = downloadItems.size();

        System.out.println("\n全部解析完成，作品总数为 " + awemeCount + "。共 " + downloadItems.size() + " 个文件待下载。");
        System.out.println("输入 y 开始下载：");

        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine();

        if (!"y".equalsIgnoreCase(input)) {
            System.out.println("用户取消下载");
            return;
        }

        while (!downloadItems.isEmpty()&& round <= maxRound) {

            System.out.println("\n========== 第 " + round + " 轮下载 ==========");

            Iterator<DownloadItem> iterator = downloadItems.iterator();

            while (iterator.hasNext()) {

                DownloadItem task = iterator.next();

                try {

                    download(task.getDownloadUrl(), task.getSavePath());

                    successCount++;

                    System.out.println("\n下载进度 " + successCount + "/" + totalDownloadCount);

                    iterator.remove();

                } catch (Exception e) {

                    System.out.println("\n=== 失败一条 ===");
                    System.out.println(task.getAwemeId());
                    System.out.println(task.getSavePath());
                    System.out.println(task.getDownloadUrl());

                }

            }

            round++;

            Thread.sleep(60000);

        }
        // ===== 所有轮次结束以后 =====

        if (!downloadItems.isEmpty()) {

            System.out.println("\n以下" + downloadItems.size() + "条文件下载失败");
            System.out.println(downloadItems);

        } else {

            System.out.println("\n全部下载成功！共" + totalDownloadCount + "个文件");

        }

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
    }

    /* ================= HTTP 工具 ================= */
    public static String httpGet(String urlStr) throws IOException {

        try {
            Thread.sleep(1000 + new Random().nextInt(3000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Referer", "https://www.douyin.com/");
        conn.setRequestProperty("Cookie", COOKIE);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes());
        }
    }

    public static void download(String urlStr, String filePath) throws IOException {

        try {
            Thread.sleep(500 + new Random().nextInt(2000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Referer", "https://www.douyin.com/");
        conn.setRequestProperty("Cookie", COOKIE);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(60000);

        int code = conn.getResponseCode();

        if (code != 200) {
            throw new IOException("HTTP " + code);
        }

        try (InputStream in = conn.getInputStream();
             OutputStream out = new FileOutputStream(filePath)) {
            in.transferTo(out);
        }

    }

}