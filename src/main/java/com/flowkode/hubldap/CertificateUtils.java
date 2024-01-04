package com.flowkode.hubldap;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

public class CertificateUtils {

    private CertificateUtils() {

    }

    public static void generateKeyStore(Path keystoreFile, String password, String cn) throws IOException, GeneralSecurityException, OperatorCreationException {
        final KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        final KeyPair keyPair = rsa.genKeyPair();
        KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("main", keyPair.getPrivate(), password.toCharArray(), new X509Certificate[]{generateCertificate(cn, keyPair, 365 * 10, "SHA256WithRSA")});
        try (FileOutputStream fos = new FileOutputStream(keystoreFile.toFile())) {
            keyStore.store(fos, password.toCharArray());
        }
    }

    private static X500Name getSubject(String dn) {
        return new X500Name(new RDN[]{new RDN(
                new AttributeTypeAndValue[]{
                        new AttributeTypeAndValue(BCStyle.DN_QUALIFIER, new DERUTF8String(dn))
                })});
    }

    private static X509Certificate generateCertificate(String dn, KeyPair keyPair, int validity, String sigAlgName) throws GeneralSecurityException, IOException, OperatorCreationException, OperatorCreationException {
//        PrivateKey privateKey = keyPair.getPrivate();
//
//        X509CertInfo info = new X509CertInfo();
//
        Date from = new Date();
        Date to = new Date(from.getTime() + validity * 1000L * 24L * 60L * 60L);
//        CertificateValidity interval = new CertificateValidity(from, to);
//        BigInteger serialNumber = new BigInteger(64, new SecureRandom());
//        X500Name owner = new X500Name(dn);
//        AlgorithmId sigAlgId = new AlgorithmId(AlgorithmId.SHA3_512_oid);
//
//        info.set(X509CertInfo.VALIDITY, interval);
//        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(serialNumber));
//        info.set(X509CertInfo.SUBJECT, owner);
//        info.set(X509CertInfo.ISSUER, owner);
//        info.set(X509CertInfo.KEY, new CertificateX509Key(keyPair.getPublic()));
//        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
//        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(sigAlgId));
//
//        // Sign the cert to identify the algorithm that's used.
//        X509CertImpl certificate = new X509CertImpl(info);
//        certificate.sign(privateKey, sigAlgName);
//
//        // Update the algorith, and resign.
//        sigAlgId = (AlgorithmId) certificate.get(X509CertImpl.SIG_ALG);
//        info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, sigAlgId);
//        certificate = new X509CertImpl(info);
//        certificate.sign(privateKey, sigAlgName);
//
//        return certificate;

        final var sn = new BigInteger(Long.SIZE, new SecureRandom());

        final var issuer = getSubject(dn);

        final var keyPublic = keyPair.getPublic();
        final var keyPublicEncoded = keyPublic.getEncoded();
        final var keyPublicInfo = SubjectPublicKeyInfo.getInstance(keyPublicEncoded);
        /*
         * First, some fiendish trickery to generate the Subject (Public-) Key Identifier...
         */
        try (final var ist = new ByteArrayInputStream(keyPublicEncoded);
             final var ais = new ASN1InputStream(ist)) {
            final var asn1Sequence = (ASN1Sequence) ais.readObject();

            final var subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(asn1Sequence);
            final var subjectPublicKeyId = new BcX509ExtensionUtils().createSubjectKeyIdentifier(subjectPublicKeyInfo);

            /*
             * Now build the Certificate, add some Extensions & sign it with our own Private Key...
             */
            final var certBuilder = new X509v3CertificateBuilder(issuer, sn, from, to, issuer, keyPublicInfo);
            final var contentSigner = new JcaContentSignerBuilder(sigAlgName).build(keyPair.getPrivate());
            /*
             * BasicConstraints instantiated with "CA=true"
             * The BasicConstraints Extension is usually marked "critical=true"
             *
             * The Subject Key Identifier extension identifies the public key certified by this certificate.
             * This extension provides a way of distinguishing public keys if more than one is available for
             * a given subject name.
             */
            final var certHolder = certBuilder
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                    .addExtension(Extension.subjectKeyIdentifier, false, subjectPublicKeyId)
                    .build(contentSigner);

            return new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certHolder);
        }
    }

}
