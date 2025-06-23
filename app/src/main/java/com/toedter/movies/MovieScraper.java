package com.toedter.movies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MovieScraper {

    final public static String MOVIEDIR = "external-movies";
    private final Logger logger = LoggerFactory.getLogger(MovieScraper.class);
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public void getMovies() throws Exception {
        WebDriver driver = new ChromeDriver();
        driver.get("https://www.imdb.com/chart/top/");

        // Scroll to bottom
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
        Thread.sleep(2000); // Wait for lazy content to load

        String pageSource = driver.getPageSource();
        driver.quit();

        // Now parse with Jsoup
        Document doc = Jsoup.parse(pageSource);

        String lastFoundId = "";
        List<String> ids = new ArrayList<>();


        // Select all <a> tags with an href attribute
        Elements links = doc.select("a[href]");

        // Define the regex pattern
        Pattern pattern = Pattern.compile("^/title/(\\w+)");

        for (Element link : links) {
            String href = link.attr("href");
            Matcher matcher = pattern.matcher(href);
            if (matcher.find()) {
                String group = matcher.group();
                String imdbId = group.substring(group.lastIndexOf('/') + 1);
                if (!lastFoundId.equals(imdbId)) {
                    ids.add(imdbId);
                }
                lastFoundId = imdbId;
            }
        }

        System.out.println("Found " + ids.size() + " unique IMDb IDs.");
        PrintWriter movieWriter = new PrintWriter(MOVIEDIR + "/movies.json", "UTF-8");
        movieWriter.println("{");
        movieWriter.println("  \"date\": \"" + simpleDateFormat.format(new Date()) + "\",");
        movieWriter.println("  \"movies\": [");

        int movieCount = ids.size();
        for (int i = 0; i < movieCount; i++) {
            String id = ids.get(i);
            RestTemplate restTemplate = new RestTemplate();
            String omdbApikey = System.getenv("OMDB_API_KEY");
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "https://www.omdbapi.com/?i=" + id + "&r=json&apikey=" + omdbApikey,
                    String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readValue(response.getBody(), JsonNode.class);

            String jsonComma = i == movieCount - 1 ? "" : ",";
            movieWriter.println("    " + rootNode + jsonComma);

            String imageURL = rootNode.get("Poster").asText();

            String movieImage = MOVIEDIR + "/thumbs/" + id + ".jpg";
            try {
                saveImage(imageURL, movieImage);
            } catch (IOException e) {
                logger.error("cannot save movie image");
            }
        }
        movieWriter.println("  ]");
        movieWriter.println("}");
        movieWriter.close();
    }

    public static void saveImage(String imageUrl, String destinationFile) throws IOException {
        URL url = new URL(imageUrl);
        InputStream inputStream = url.openStream();
        OutputStream outputStream = new FileOutputStream(destinationFile);

        byte[] b = new byte[2048];
        int length;

        while ((length = inputStream.read(b)) != -1) {
            outputStream.write(b, 0, length);
        }

        inputStream.close();
        outputStream.close();
    }

    public static void main(String[] args) throws Exception {
        MovieScraper imdbReader = new MovieScraper();
        imdbReader.getMovies();
    }
}