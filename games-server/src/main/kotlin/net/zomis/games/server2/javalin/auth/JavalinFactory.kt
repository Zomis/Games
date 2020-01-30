package net.zomis.games.server2.javalin.auth

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPrivateKey
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.security.KeyStoreException
import java.io.IOException
import org.eclipse.jetty.util.ssl.SslContextFactory
import java.security.KeyStore
import java.io.File.separator
import net.zomis.games.server2.ServerConfig
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.ServerConnector
import io.javalin.Javalin
import org.apache.commons.codec.binary.Base64
import org.eclipse.jetty.server.Server
import java.io.File
import java.nio.file.Files
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

object JavalinFactory {

    fun javalin(serverConfig: ServerConfig): Javalin {
        return Javalin.create().server { createServer(serverConfig) }
    }

    private fun createServer(serverConfig: ServerConfig): Server {
        val server = Server()
        val connector = ServerConnector(server)
        connector.port = serverConfig.webSocketPort
        if (serverConfig.useSecureWebsockets()) {
            val sslConnector = ServerConnector(server, createSslContextFactory(serverConfig))
            sslConnector.port = serverConfig.webSocketPortSSL
            server.connectors = arrayOf<Connector>(sslConnector, connector)
        } else {
            server.connectors = arrayOf<Connector>(connector)
        }
        return server
    }

    private fun createSslContextFactory(serverConfig: ServerConfig): SslContextFactory {
        val pathTo = serverConfig.certificatePath
        val keyPassword = serverConfig.certificatePassword!!
        try {
            val certBytes = parseDERFromPEM(Files.readAllBytes(File(pathTo + separator + "cert.pem").toPath()), "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----")
            val keyBytes = parseDERFromPEM(Files.readAllBytes(File(pathTo + separator + "privkey.pem").toPath()), "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----")

            val cert = generateCertificateFromDER(certBytes)
            val key = generatePrivateKeyFromDER(keyBytes)

            val keystore = KeyStore.getInstance("PKCS12")
            keystore.load(null)
            keystore.setCertificateEntry("cert-alias", cert)
            keystore.setKeyEntry("key-alias", key, keyPassword.toCharArray(), arrayOf(cert))

            val sslContextFactory = SslContextFactory()
            sslContextFactory.keyStore = keystore
            sslContextFactory.setKeyStorePassword(keyPassword)
            return sslContextFactory
        } catch (e: IOException) {
            throw IllegalArgumentException(e)
        } catch (e: KeyStoreException) {
            throw IllegalArgumentException(e)
        } catch (e: InvalidKeySpecException) {
            throw IllegalArgumentException(e)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalArgumentException(e)
        } catch (e: CertificateException) {
            throw IllegalArgumentException(e)
        }

    }

    private fun parseDERFromPEM(pem: ByteArray, beginDelimiter: String, endDelimiter: String): ByteArray {
        val data = String(pem)
        var tokens = data.split(beginDelimiter.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        tokens = tokens[1].split(endDelimiter.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return Base64.decodeBase64(tokens[0])
    }

    @Throws(InvalidKeySpecException::class, NoSuchAlgorithmException::class)
    private fun generatePrivateKeyFromDER(keyBytes: ByteArray): RSAPrivateKey {
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePrivate(spec) as RSAPrivateKey
    }

    @Throws(CertificateException::class)
    private fun generateCertificateFromDER(certBytes: ByteArray): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")

        return factory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
    }

}
