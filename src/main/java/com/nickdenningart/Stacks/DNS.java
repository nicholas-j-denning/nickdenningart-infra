package com.nickdenningart.Stacks;

import software.constructs.Construct;

import java.util.List;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.CnameRecord;
import software.amazon.awscdk.services.route53.PublicHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;

public class DNS extends Stack {

    private final PublicHostedZone hostedZone;
    private final Certificate certificate;
    private final Certificate imageCertificate;

    public DNS(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Route53 hz
        hostedZone = PublicHostedZone.Builder.create(this, "NDAHostedZone")
            .zoneName("nickdenningart.com")
            .build();
        
        // SSL certificates in acm
        certificate = Certificate.Builder.create(this, "NDACertificate")
            .domainName("nickdenningart.com")
            .validation(CertificateValidation.fromDns(hostedZone))
            .build();
        imageCertificate = Certificate.Builder.create(this, "NDAImageCertificate")
            .domainName("images.nickdenningart.com")
            .validation(CertificateValidation.fromDns(hostedZone))
            .build();
       
        // Set up shopify DNS
        CnameRecord.Builder.create(this, "NDAShopifyCNAME")
            .zone(hostedZone)
            .recordName("shop.nickdenningart.com")
            .domainName("shops.myshopify.com.")
            .build();
        
    }

    public PublicHostedZone getHostedzone(){return hostedZone;}
    public Certificate getCertificate(){return certificate;}
    public Certificate getImageCertificate(){return imageCertificate;}
}
