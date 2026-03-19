package com.codeguard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubPullRequestEvent {

    private String action;

    @JsonProperty("pull_request")
    private PullRequest pullRequest;

    private Repository repository;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PullRequest {
        private int number;
        private String title;
        private String body;
        private String diff;
        private List<Label> labels;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        private String name;

        @JsonProperty("full_name")
        private String fullName;

        private Owner owner;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Owner {
        private String login;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Label {
        private String name;
        private String color;
    }
}
