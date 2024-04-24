package com.nickdenningart.Stacks;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.LambdaRestApi;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.cloudfront.AllowedMethods;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.origins.S3Origin;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildEnvironmentVariable;
import software.amazon.awscdk.services.codebuild.EventAction;
import software.amazon.awscdk.services.codebuild.FilterGroup;
import software.amazon.awscdk.services.codebuild.GitHubSourceProps;
import software.amazon.awscdk.services.codebuild.ISource;
import software.amazon.awscdk.services.codebuild.LinuxBuildImage;
import software.amazon.awscdk.services.codebuild.Project;
import software.amazon.awscdk.services.codebuild.Source;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Billing;
import software.amazon.awscdk.services.dynamodb.TableClass;
import software.amazon.awscdk.services.dynamodb.TableV2;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Policy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SnapStartConf;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.CloudFrontTarget;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.CorsRule;
import software.amazon.awscdk.services.s3.HttpMethods;
import software.amazon.awscdk.services.s3.ObjectOwnership;
import software.amazon.awscdk.services.ssm.ParameterTier;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

public class Backend extends Stack {

    public Backend(final Construct scope, final String id, final StackProps props, HostedZone hostedZone, Certificate imageCertificate) {
        super(scope, id, props);

        // Production S3 bucket to hold gallery.json
        Bucket prodGalleryBucket = Bucket.Builder.create(this, "NDAProdGalleryBucket")
            .bucketName("prod-nickdenningart-gallery")
            .encryption(BucketEncryption.S3_MANAGED)
            .publicReadAccess(true)
            .objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED)
            .cors(List.of(CorsRule.builder()
                .allowedMethods(List.of(HttpMethods.GET))
                .allowedOrigins(List.of("*"))
                .allowedHeaders(List.of("*"))
                .build()))
            // Don't delete bucket if stack is deleted
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();
       
        // ssm parameter of bucket name
        StringParameter prodGalleryBucketParameter = StringParameter.Builder.create(this, "NDAProdGalleryBucketParameter")
            .tier(ParameterTier.STANDARD)
            .parameterName("/nickdenningart/prod/gallery-bucket")
            .stringValue(prodGalleryBucket.getBucketName())
            .build();

        // dev S3 bucket to hold gallery.json
        Bucket devGalleryBucket = Bucket.Builder.create(this, "NDADevGalleryBucket")
            .bucketName("dev-nickdenningart-gallery")
            .encryption(BucketEncryption.S3_MANAGED)
            .publicReadAccess(true)
            .blockPublicAccess(BlockPublicAccess.BLOCK_ACLS)
            .cors(List.of(CorsRule.builder()
                .allowedMethods(List.of(HttpMethods.GET))
                .allowedOrigins(List.of("*"))
                .allowedHeaders(List.of("*"))
                .build()))
            .removalPolicy(RemovalPolicy.DESTROY)
            .build();

        // ssm parameter of bucket name
        StringParameter devGalleryBucketParameter = StringParameter.Builder.create(this, "NDADevGalleryBucketParameter")
            .tier(ParameterTier.STANDARD)
            .parameterName("/nickdenningart/dev/gallery-bucket")
            .stringValue(devGalleryBucket.getBucketName())
            .build();


        // prod S3 bucket to hold images
        Bucket prodImageBucket = Bucket.Builder.create(this, "NDAProdImageBucket")
            .bucketName("prod-nickdenningart-image")
            .encryption(BucketEncryption.S3_MANAGED)
            .publicReadAccess(true)
            .blockPublicAccess(BlockPublicAccess.BLOCK_ACLS)
            .cors(List.of(CorsRule.builder()
                .allowedMethods(List.of(HttpMethods.GET, HttpMethods.PUT))
                .allowedOrigins(List.of("*"))
                .allowedHeaders(List.of("*"))
                .build()))
            // Don't delete bucket if stack is deleted
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();

        // ssm parameter of bucket name
        StringParameter prodImageBucketParameter = StringParameter.Builder.create(this, "NDAProdImageBucketParameter")
            .tier(ParameterTier.STANDARD)
            .parameterName("/nickdenningart/prod/image-bucket")
            .stringValue(prodImageBucket.getBucketName())
            .build();
        
        // Cloudfront distribution
        Distribution distribution = Distribution.Builder.create(this, "NDAImageCloudFront")
            .domainNames(List.of("images.nickdenningart.com"))
            .certificate(imageCertificate)
            .defaultBehavior(BehaviorOptions.builder()
                .origin(new S3Origin(prodImageBucket))
                .allowedMethods(AllowedMethods.ALLOW_GET_HEAD_OPTIONS)
                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                .build())
            .build();
       
            // Create DNS record for cloudfront
            AaaaRecord.Builder.create(this, "NDAImageCloudFrontDNSRecord")
                .recordName("images.nickdenningart.com")
                .zone(hostedZone)
                .target(RecordTarget.fromAlias(new CloudFrontTarget(distribution)))
                .build();
       
        // dev S3 bucket to hold images
        Bucket devImageBucket = Bucket.Builder.create(this, "NDADevImageBucket")
            .bucketName("dev-nickdenningart-image")
            .encryption(BucketEncryption.S3_MANAGED)
            .publicReadAccess(true)
            .blockPublicAccess(BlockPublicAccess.BLOCK_ACLS)
            .cors(List.of(CorsRule.builder()
                .allowedMethods(List.of(HttpMethods.GET, HttpMethods.PUT))
                .allowedOrigins(List.of("*"))
                .allowedHeaders(List.of("*"))
                .build()))
            .removalPolicy(RemovalPolicy.DESTROY)
            .build();

        // ssm parameter of bucket name
        StringParameter devImageBucketParameter = StringParameter.Builder.create(this, "NDADevImageBucketParameter")
            .tier(ParameterTier.STANDARD)
            .parameterName("/nickdenningart/dev/image-bucket")
            .stringValue(devImageBucket.getBucketName())
            .build();
       
        // prod-fractal dynamodb table
        TableV2 prodFractalTable = TableV2.Builder.create(this, "NDAFractalTable")
            .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
            .tableName("prod-fractal")
            .billing(Billing.onDemand())
            .deletionProtection(true)
            .build();
        
        // prod-fractal dynamodb table
        TableV2 devFractalTable = TableV2.Builder.create(this, "NDADevFractalTable")
            .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
            .tableName("dev-fractal")
            .billing(Billing.onDemand())
            .deletionProtection(true)
            .build();
       
        // make role for /fractal lambda
        Role functionRole = Role.Builder.create(this, "NDAFractalLambdaRole")
            .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
            .build();
        functionRole.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
        functionRole.addToPolicy(PolicyStatement.Builder.create()
            .actions(List.of("ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"))
            .effect(Effect.ALLOW)
            .resources(List.of("arn:aws:ssm:us-west-1:855926785504:parameter/*"))
            .build());
        prodFractalTable.grantReadWriteData(functionRole);
        prodImageBucket.grantReadWrite(functionRole);
        prodGalleryBucket.grantReadWrite(functionRole);
        
        // Lambda function for /fractal
        Function fractalFunction = Function.Builder.create(this, "FractalFunction")
            .runtime(Runtime.JAVA_17)
            .handler("com.nickdenningart.fractal.Handler")
            // only here because code is required
            // should be replaced by running codebuild
            .code(Code.fromAsset("app.jar"))
            .memorySize(2048)
            .timeout(Duration.minutes(2))
            .snapStart(SnapStartConf.ON_PUBLISHED_VERSIONS)
            .environment(Map.of(
                "MAIN_CLASS", "com.nickdenningart.gallery.App",
                "SPRING_PROFILES_ACTIVE","prod"))  
            .role(functionRole)
            .build();
        
        LambdaRestApi api = LambdaRestApi.Builder.create(this, "FractalApi")
            .handler(fractalFunction)
            .build();

        // Automatically configure webhooks on github for codebuild
        ISource gitHubSource = Source.gitHub(GitHubSourceProps.builder()
            .owner("nicholas-j-denning")
            .repo("nickdenningart-fractal")
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
        Project build = Project.Builder.create(this, "NDABackendCodeBuild")
            .environment(BuildEnvironment.builder()
                .buildImage(LinuxBuildImage.AMAZON_LINUX_2_5)
                .build())
            // Place built files in bucket root
            .source(gitHubSource)
            .build();
        // grant codebuild permissions
        build.getRole().attachInlinePolicy(Policy.Builder.create(this, "NDACodeBuildRole")
            .statements(List.of(
                // permission to update prod lambda
                PolicyStatement.Builder.create()
                    .actions(List.of("lambda:UpdateFunctionCode"))
                    .effect(Effect.ALLOW)
                    .resources(List.of(fractalFunction.getFunctionArn()))
                    .build(),
                // permission to fetch from parameter store
                PolicyStatement.Builder.create()
                    .actions(List.of("ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"))
                    .effect(Effect.ALLOW)
                    .resources(List.of("arn:aws:ssm:us-west-1:855926785504:parameter/*"))
                    .build()))
            .build());
        // permission to access dev db table for tests
        devFractalTable.grantReadWriteData(build.getRole());
        // permission to access dev buckets for tests
        devImageBucket.grantReadWrite(build.getRole());
        devGalleryBucket.grantReadWrite(build.getRole());


    }
}

