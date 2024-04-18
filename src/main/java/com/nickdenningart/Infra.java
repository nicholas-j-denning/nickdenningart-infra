package com.nickdenningart;

import com.nickdenningart.Stacks.Backend;
import com.nickdenningart.Stacks.DNS;
import com.nickdenningart.Stacks.Frontend;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class Infra {
    public static void main(final String[] args) {
        App app = new App();

        DNS dns = new DNS(app, "NDADNS", StackProps.builder()
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region("us-east-1")
                        .build())
                .build());

        new Frontend(app, "InfraStack", StackProps.builder()
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region("us-west-1")
                        .build())
                .crossRegionReferences(true)
                .build(),
                dns.getHostedzone(),
                dns.getCertificate());

        new Backend(app, "NDABackend", StackProps.builder()
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region("us-west-1")
                        .build())
                .build());

        app.synth();
    }
}

