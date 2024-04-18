package com.nickdenningart.Stacks;

import software.constructs.Construct;

import java.util.List;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.cloudfront.AllowedMethods;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.ErrorResponse;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.origins.S3Origin;
import software.amazon.awscdk.services.codebuild.Artifacts;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.EventAction;
import software.amazon.awscdk.services.codebuild.FilterGroup;
import software.amazon.awscdk.services.codebuild.GitHubSourceProps;
import software.amazon.awscdk.services.codebuild.ISource;
import software.amazon.awscdk.services.codebuild.LinuxBuildImage;
import software.amazon.awscdk.services.codebuild.Project;
import software.amazon.awscdk.services.codebuild.S3ArtifactsProps;
import software.amazon.awscdk.services.codebuild.Source;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.PublicHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.CloudFrontTarget;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;

public class Frontend extends Stack {

    public Frontend(final Construct scope, final String id, final StackProps props, final PublicHostedZone hostedZone, final Certificate certificate) {
        super(scope, id, props);

        // S3 bucket to hold frontend
        Bucket bucket = Bucket.Builder.create(this, "NDAFrontend")
            .bucketName("nickdenningart.com")
            // needed for s3 website
            .encryption(BucketEncryption.S3_MANAGED)
            .publicReadAccess(true)
            .websiteIndexDocument("index.html")
            .websiteErrorDocument("index.html")
            // Don't delete bucket if stack is deleted
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();

        // Automatically configure webhooks on github for codebuild
        ISource gitHubSource = Source.gitHub(GitHubSourceProps.builder()
            .owner("nicholas-j-denning")
            .repo("nickdenningart-frontend")
            .webhook(true)
            // .webhookFilters(List.of(
            //     FilterGroup.inEventOf(EventAction.PUSH)
            //         .andBranchIs("master"),
            //     FilterGroup.inEventOf(EventAction.PULL_REQUEST_MERGED)
            //         .andBranchIs("master")
            // ))
            .build());
       
        // Codebuild triggered by github webhook
        // uses buildspec.yaml in frontend source repo
        Project.Builder.create(this, "NDAFrontendCodeBuild")
            .environment(BuildEnvironment.builder()
                .buildImage(LinuxBuildImage.AMAZON_LINUX_2_5)
                .build())
            // Place built files in bucket root
            .artifacts(Artifacts.s3(S3ArtifactsProps.builder()
                .path("/")
                .includeBuildId(false)
                .bucket(bucket)
                .encryption(false)
                .packageZip(false)
                .build()))
            .source(gitHubSource)
            .build();
        
        // Cloudfront distribution
        Distribution distribution = Distribution.Builder.create(this, "NDAFrontendCloudFront")
            .domainNames(List.of("nickdenningart.com"))
            .certificate(certificate)
            .defaultBehavior(BehaviorOptions.builder()
                .origin(new S3Origin(bucket))
                .allowedMethods(AllowedMethods.ALLOW_GET_HEAD_OPTIONS)
                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                .build())
            .errorResponses(List.of(
                ErrorResponse.builder()
                    .httpStatus(404)   
                    .responseHttpStatus(200)
                    .responsePagePath("/index.html")
                    .build()
            ))
            .build();
       
            // Create DNS record for cloudfront
            AaaaRecord.Builder.create(this, "NDAFrontendCloudFrontDNSRecord")
                .zone(hostedZone)
                .target(RecordTarget.fromAlias(new CloudFrontTarget(distribution)))
                .build();
    }
}
