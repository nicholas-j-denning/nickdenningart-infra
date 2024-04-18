package com.nickdenningart.Stacks;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.LambdaRestApi;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
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
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.CorsRule;
import software.amazon.awscdk.services.s3.HttpMethods;
import software.constructs.Construct;

public class Backend extends Stack {

    public Backend(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // S3 bucket to hold gallery.json
        Bucket galleryBucket = Bucket.Builder.create(this, "NDAGallery")
            .bucketName("nickdenningart-gallery")
            .encryption(BucketEncryption.S3_MANAGED)
            .publicReadAccess(true)
            .blockPublicAccess(BlockPublicAccess.BLOCK_ACLS)
            .cors(List.of(CorsRule.builder()
                .allowedMethods(List.of(HttpMethods.GET))
                .allowedOrigins(List.of("*"))
                .allowedHeaders(List.of("*"))
                .build()))
            // Don't delete bucket if stack is deleted
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();
       
        // prod-fractal dynamodb table
        TableV2 prodFractalTable = TableV2.Builder.create(this, "NDAFractalTable")
            .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
            .tableName("prod-fractal")
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
        
        // Lambda function for /fractal
        Function fractalFunction = Function.Builder.create(this, "FractalFunction")
            .runtime(Runtime.JAVA_17)
            .handler("com.nickdenningart.fractal.Handler")
            // only here because code is required
            // should be replaced by running pipeline
            .code(Code.fromAsset("app.jar"))
            .memorySize(1024)
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
                .environmentVariables(Map.of())
                .build())
            // Place built files in bucket root
            .source(gitHubSource)
            .build();
        
        // grant codebuild permission to update lambda code
        build.getRole().attachInlinePolicy(Policy.Builder.create(this, "NDACodeBuildRole")
            .statements(List.of(
                PolicyStatement.Builder.create()
                    .actions(List.of("lambda:UpdateFunctionCode"))
                    .effect(Effect.ALLOW)
                    .resources(List.of(fractalFunction.getFunctionArn()))
                    .build()))
            .build());

    }
}

