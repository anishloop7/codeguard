package com.codeguard.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewRequest {

    private String owner;
    private String repositoryName;
    private int prNumber;
    private String prTitle;
    private String prDescription;
    private String diff;
    private ReviewProfile profile;
}
