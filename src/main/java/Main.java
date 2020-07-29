import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {

    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpHeaders SPOTIFY_HEADERS = getSpotifyHeaders();
    private static final RestTemplate REST_TEMPLATE = getRestTemplate();
    private static final String LAST_FM_API_KEY = "ff341e2c1cc09a23b195f001440e9b7a";

    private static final String LAST_FM_USER_NAME = "[YOU_LAST_FM_USER_NAME]";
    // Get one from https://developer.spotify.com/console/put-current-user-saved-tracks
    private static final String SPOTIFY_AUTH_HEADER = "Bearer [JTW]";


    public static void main(String[] args) {
        try {

            var lastFmTracks = getLastFmLovedTracks();
            var spotifyTracks = searchOnSpotify(lastFmTracks);
            saveToSpotifyLikedSongs(spotifyTracks);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<LastFmTrack> getLastFmLovedTracks() throws URISyntaxException {
        var request = new HttpEntity(null, null);
        var uri = "http://ws.audioscrobbler.com/2.0/?method=user.getlovedtracks&user=" + LAST_FM_USER_NAME + "&api_key=" + LAST_FM_API_KEY + "&format=json&limit=1000";
        var response = REST_TEMPLATE.exchange(new URI(uri), HttpMethod.GET, request, String.class);
        var lastFmResponse = GSON.fromJson(response.getBody(), LastFmResponse.class);

        return lastFmResponse.getLovedtracks().getTrack();
    }

    private static List<SpotifyTrack> searchOnSpotify(List<LastFmTrack> lastFmTracks) throws InterruptedException, UnsupportedEncodingException, URISyntaxException {
        var request = new HttpEntity(SPOTIFY_HEADERS);
        var spotifyTracks = new ArrayList<SpotifyTrack>();
        var notFound = new ArrayList<LastFmTrack>();

        for (var lastFmTrack : lastFmTracks) {
            var uri = "https://api.spotify.com/v1/search?q=" + encode(lastFmTrack.getArtist().getName()) + "+" + encode(lastFmTrack.getName()) + "&type=track&market=PL&limit=1";
            Thread.sleep(100);
            var response = REST_TEMPLATE.exchange(new URI(uri), HttpMethod.GET, request, String.class);
            var spotifyResponse = GSON.fromJson(response.getBody(), SpotifyResponse.class);
            if (spotifyResponse.getTracks().getItems().size() > 0)
                spotifyTracks.add(spotifyResponse.getTracks().getItems().get(0));
            else
                notFound.add(lastFmTrack);
        }

        for (var nf : notFound)
            System.out.println("Not found on Spotify: " + nf.getArtist().getName() + " - " + nf.getName());

        return spotifyTracks;
    }

    private static void saveToSpotifyLikedSongs(List<SpotifyTrack> spotifyTracks) throws InterruptedException, URISyntaxException, UnsupportedEncodingException {
        var sb = new StringBuilder();
        var request = new HttpEntity(SPOTIFY_HEADERS);
        for (int i = spotifyTracks.size() - 1; i >= 0; i--) {
            var track = spotifyTracks.get(i);
            sb.append(track.getId()).append(",");
            if ((i % 10 == 0 && i != spotifyTracks.size() - 1) || i == 0) {
                sb.setLength(sb.length() - 1);
                var uriString = "https://api.spotify.com/v1/me/tracks?ids=" + encode(sb.toString());
                Thread.sleep(100);
                REST_TEMPLATE.exchange(new URI(uriString), HttpMethod.PUT, request, String.class);
                sb.setLength(0);
            }
        }
    }

    private static String encode(String str) throws UnsupportedEncodingException {
        return URLEncoder.encode(str, StandardCharsets.UTF_8.toString());
    }

    private static HttpHeaders getSpotifyHeaders() {
        var spotifyHeaders = new HttpHeaders();
        spotifyHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        spotifyHeaders.setContentType(MediaType.APPLICATION_JSON);
        spotifyHeaders.set("Authorization", SPOTIFY_AUTH_HEADER);

        return spotifyHeaders;
    }

    private static RestTemplate getRestTemplate() {
        var restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

        return restTemplate;
    }
}
