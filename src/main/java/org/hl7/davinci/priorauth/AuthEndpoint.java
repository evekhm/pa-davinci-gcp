package org.hl7.davinci.priorauth;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.directory.InvalidAttributeValueException;
import javax.servlet.http.HttpServletRequest;
import javax.transaction.NotSupportedException;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

import org.hl7.davinci.priorauth.Database.Table;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/auth")
public class AuthEndpoint {

    static final Logger logger = PALogger.getLogger();
    static final SecureRandom random = new SecureRandom();

    static final String INVALID_REQUEST = "invalid_request";
    static final String INVALID_CLIENT = "invalid_client";

    @PostMapping(value = "/register", consumes = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
    public ResponseEntity<String> registerClient(HttpServletRequest request, HttpEntity<String> entity) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String body = entity.getBody();
        String clientId = UUID.randomUUID().toString();
        try {
            // Read the body
            JSONObject jsonObject = (JSONObject) new JSONParser().parse(body);
            status = HttpStatus.BAD_REQUEST;
            JSONObject jwks = (JSONObject) jsonObject.get("jwks");
            String jwks_url = (String) jsonObject.get("jwks_url");
            status = HttpStatus.OK;

            // Add the new client to the database
            HashMap<String, Object> dataMap = new HashMap<String, Object>();
            dataMap.put("id", clientId);
            dataMap.put("jwks", jwks.toJSONString());
            dataMap.put("jwks_url", jwks_url);
            App.getDB().write(Table.CLIENT, dataMap);
        } catch (ParseException e) {
            logger.log(Level.SEVERE, "AuthEndpoint::registerClient:Unable to parse body\n" + body, e);
        }

        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body("{ client_id: " + clientId + " }");
    }

    @PostMapping(value = "/token")
    public ResponseEntity<String> token(HttpServletRequest request,
            @RequestParam(name = "scope", required = true) String scope,
            @RequestParam(name = "grant_type", required = true) String grantType,
            @RequestParam(name = "client_assertion_type", required = true) String clientAssertionType,
            @RequestParam(name = "client_assertion", required = true) String token) {
        // TODO create audit event
        logger.info("AuthEndpoint::token:scope=" + scope + "&grant_type=" + grantType + "&client_assertion_type="
                + clientAssertionType + "&client_assertion=" + token);
        // Check client_credentials and client_assertion_type
        if (!grantType.equals("client_credentials")
                && !clientAssertionType.equals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")) {
            String message = "AuthEndpoint::token:Invalid grant_type or client_assertion_type";
            logger.warning(message);
            return sendError(INVALID_REQUEST, message);
        }

        String[] jwtParts = token.split("\\.");
        if (jwtParts.length != 3) {
            return sendError(INVALID_REQUEST, "client_assertion is not a valid jwt token");
        }

        String clientId;
        String jwtHeaderRaw = new String(Base64.getUrlDecoder().decode(jwtParts[0]));
        String jwtBodyRaw = new String(Base64.getUrlDecoder().decode(jwtParts[1]));
        try {
            JSONObject jwtHeader = (JSONObject) new JSONParser().parse(jwtHeaderRaw);
            JSONObject jwtBody = (JSONObject) new JSONParser().parse(jwtBodyRaw);
            String alg = (String) jwtHeader.get("alg");
            String kid = (String) jwtHeader.get("kid");
            String iss = (String) jwtBody.get("iss");
            clientId = iss;
            logger.info("AuthEndpoint::alg:" + alg + " kid:" + kid + " iss: " + iss);

            if (!alg.equals("RS384")) {
                // if (!alg.equals("RS384") || !alg.equals("EC384")) {
                return sendError(INVALID_REQUEST, "JWT algorithm not supported. Must be RS384 or EC384.");
            }

            // Get keys and validate signature
            JSONObject jwks = getJwks(iss);
            List<RSAPublicKey> keys = getKeys(jwks, kid);
            boolean verified = false;
            for (RSAPublicKey publicKey : keys) {
                if (tokenIsValid(token, publicKey, iss)) {
                    verified = true;
                    break;
                }
            }
            if (!verified)
                throw new JWTVerificationException("None of the provided jwk validated the client_assertion");
        } catch (ParseException e) {
            logger.log(Level.SEVERE, "AuthEndpoint::Parse Exception decoding jwt", e);
            return sendFailure();
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "AuthEndpoint::No Such Algorithm Exception verifying jwt", e);
            return sendFailure();
        } catch (InvalidKeySpecException e) {
            logger.log(Level.SEVERE, "AuthEndpoint::Invalid Key Spec Exception verifying jwt", e);
            return sendFailure();
        } catch (InvalidAttributeValueException e) {
            return sendError(INVALID_CLIENT, "No client registered with that id. Please register first");
        } catch (NotSupportedException e) {
            return sendError(INVALID_REQUEST, "Only supports jwks and not jwks_url");
        } catch (JWTVerificationException e) {
            return sendError(INVALID_CLIENT, "Could not verify client using any of the keys in the jwk set");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unknown exception occurred", e);
            return sendError(INVALID_CLIENT, "Could not verify client.");
        }

        // Verification was successful - authorize
        final String authToken = generateAuthToken();

        if (clientId != null)
            App.getDB().update(Table.CLIENT, Collections.singletonMap("id", clientId),
                    Collections.singletonMap("token", authToken));

        // Create response
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("access_token", authToken);
        response.put("token_type", "bearer");
        response.put("expires_in", 300);
        response.put("scope", "system/*.read");
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(JSONObject.toJSONString(response));
    }

    /**
     * Generate a new auth token using SecureRandom
     * 
     * @return authorization token
     */
    private static String generateAuthToken() {
        byte[] bytes = new byte[15];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Create a new ResponseEntity representing an error response
     * (https://tools.ietf.org/html/rfc6749#page-45)
     * 
     * @param error       - the error type
     * @param description - brief description of the error
     * @return BAD REQUEST error response
     */
    private static ResponseEntity<String> sendError(String error, String description) {
        Map<String, String> map = new HashMap<String, String>();
        map.put("error", error);
        map.put("error_description", description);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body(JSONObject.toJSONString(map));
    }

    /**
     * Create a new ResponseEntity for an internal server error
     * 
     * @return INTERNAL SERVER ERROR response (no body)
     */
    private static ResponseEntity<String> sendFailure() {
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Get the jwk store for the given client from when they registered
     * 
     * @param clientId - the id of the client to get the jwks
     * @return jwk store for the client or exception
     * @throws ParseException                 JSON Parse error
     * @throws InvalidAttributeValueException No jwks or jwks_url for the given
     *                                        client id
     * @throws NotSupportedException          RI does not support jwks_url right now
     */
    private static JSONObject getJwks(String clientId)
            throws ParseException, InvalidAttributeValueException, NotSupportedException {
        String jwks = App.getDB().readString(Table.CLIENT, Collections.singletonMap("id", clientId), "jwks");
        if (jwks == null) {
            String jwks_url = App.getDB().readString(Table.CLIENT, Collections.singletonMap("id", clientId),
                    "jwks_url");
            // TODO: get the jwks
            if (jwks_url == null)
                throw new InvalidAttributeValueException();
            else
                throw new NotSupportedException("Only jwks is supported right now");
        }

        // Convert to JSONObject
        return (JSONObject) new JSONParser().parse(jwks);
    }

    /**
     * Get keys which match kid from jwks
     * 
     * @param jwks - the jwk store
     * @param kid  - the key id to match
     * @return List of RSA Public Keys from the jwk store which match kid
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     */
    private static List<RSAPublicKey> getKeys(JSONObject jwks, String kid)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        // TODO: Support EC384 keys
        RSAPublicKeySpec publicKeySpec;
        KeyFactory kf = KeyFactory.getInstance("RSA");
        List<RSAPublicKey> validKeys = new ArrayList<RSAPublicKey>();

        // Iterate over all the keys in the jwk set
        JSONArray keys = (JSONArray) jwks.get("keys");
        for (Object i : keys) {
            JSONObject key = (JSONObject) i;
            String keyId = (String) key.get("kid");
            if (keyId.equals(kid)) {
                String eRaw = (String) key.get("e");
                String nRaw = (String) key.get("n");
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(eRaw));
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(nRaw));
                publicKeySpec = new RSAPublicKeySpec(n, e);
                validKeys.add((RSAPublicKey) kf.generatePublic(publicKeySpec));
            }
        }

        return validKeys;
    }

    /**
     * Validate a token
     * 
     * @param token     - the token to be validated
     * @param publicKey - RSA Public Key used to check token signature
     * @param clientId  - the client id who issued this token
     * @return true if the token is valid, false otherwise
     */
    private static boolean tokenIsValid(String token, RSAPublicKey publicKey, String clientId) {
        try {
            Algorithm algorithm = Algorithm.RSA384(publicKey, null);
            JWTVerifier verifier = JWT.require(algorithm).withSubject(clientId)
                    .withAudience(App.getBaseUrl() + "/auth").build();
            verifier.verify(token);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
