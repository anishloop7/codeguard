package com.codeguard.dto;

import com.codeguard.model.ReviewProfile;
import lombok.Data;

@Data
public class PullRequestDTO {

    private String owner;
    private String repositoryName;
    private int prNumber;
    private String prTitle;
    private String prDescription;
    private String diff;
    private ReviewProfile profile;
}
