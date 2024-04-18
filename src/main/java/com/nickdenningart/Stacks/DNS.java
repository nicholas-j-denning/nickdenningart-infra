package com.nickdenningart.Stacks;

import software.constructs.Construct;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.route53.PublicHostedZone;

public class DNS extends Stack {
    public DNS(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public DNS(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Route53 hz
        PublicHostedZone hostedZone = PublicHostedZone.Builder.create(this, "NDAHostedZone")
            .zoneName("nickdenningart.com")
            .build();
        
        Certificate certificate = Certificate.Builder.create(this, "NDACertificate")
            .domainName("nickdenningart.com")
            .validation(CertificateValidation.fromDns(hostedZone))
            .build();
    }
}
