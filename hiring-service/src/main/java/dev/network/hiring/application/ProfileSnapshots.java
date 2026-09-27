package dev.network.hiring.application;

import dev.network.web.ServiceHttp;

import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProfileSnapshots {
    private final ServiceHttp http;
    private final String memberUrl;

    public ProfileSnapshots(
            ServiceHttp http, @Value("${MEMBER_URL:http://localhost:8081}") String memberUrl) {
        this.http = http;
        this.memberUrl = memberUrl;
    }

    public Snapshot get(String actor) {
        var p =
                http.post(
                        memberUrl + "/internal/v1/hiring/profile-snapshot",
                        Map.of("memberId", actor),
                        Snapshot.class);
        if (p == null
                || !actor.equals(p.id())
                || p.version() < 0
                || p.displayName() == null
                || p.experiences() == null
                || p.experiences().size() > 10)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Profile snapshot unavailable; retry request");
        return p;
    }

    public record Experience(String company, String title, String startMonth, String endMonth) {
    }

    public record Snapshot(
            String id,
            long version,
            String displayName,
            String headline,
            String summary,
            String location,
            List<Experience> experiences) {
    }
}
