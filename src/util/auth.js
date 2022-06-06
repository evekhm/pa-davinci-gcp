import KJUR, { KEYUTIL } from 'jsrsasign';
import config from '../properties.json';

function makeid() {
    var text = [];
    var possible = "---ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    for (var i = 0; i < 25; i++)
        text.push(possible.charAt(Math.floor(Math.random() * possible.length)));

    return text.join('');
}

function login() {

    const tokenUrl = (process.env.REACT_APP_AUTH ? process.env.REACT_APP_AUTH : config.auth) + "/realms/" + (process.env.REACT_APP_REALM ? process.env.REACT_APP_REALM : config.realm) + "/protocol/openid-connect/token"
    let params = {
        grant_type: "password",
        username: (process.env.REACT_APP_USER ? process.env.REACT_APP_USER : config.user),
        password: (process.env.REACT_APP_PASSWORD ? process.env.REACT_APP_PASSWORD : config.password),
        client_id: (process.env.REACT_APP_CLIENT ? process.env.REACT_APP_CLIENT : config.client)
    }

    // Encodes the params to be compliant with
    // x-www-form-urlencoded content type.
    const searchParams = Object.keys(params).map((key) => {
        return encodeURIComponent(key) + '=' + encodeURIComponent(params[key]);
    }).join('&');
    // We get the token from the url
    return fetch(tokenUrl, {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded"
        },
        body: searchParams
    });
}

function createJwt(keypair, baseUrl, cdsUrl) {
    console.log("creating jwt");
    const currentTime = KJUR.jws.IntDate.get('now');
    const endTime = KJUR.jws.IntDate.get('now + 1day');
    const kid = KJUR.jws.JWS.getJWKthumbprint(keypair.public)

    const header = {
        "alg": "RS256",
        "typ": "JWT",
        "kid": kid,
        "jku": (process.env.REACT_APP_PUBLIC_KEYS ? process.env.REACT_APP_PUBLIC_KEYS : config.public_keys)
    };

    const body = {
        "iss": baseUrl,
        "aud": cdsUrl,
        "iat": currentTime,
        "exp": endTime,
        "jti": makeid()
    }

    var sJWT = KJUR.jws.JWS.sign("RS256", JSON.stringify(header), JSON.stringify(body), keypair.private)
    return sJWT;
}

function setupKeys(callback) {
  const {prvKeyObj, pubKeyObj} = KEYUTIL.generateKeypair('RSA', 2048);
  const jwkPrv2 = KEYUTIL.getJWKFromKey(prvKeyObj);
  const jwkPub2 = KEYUTIL.getJWKFromKey(pubKeyObj);
  const kid = KJUR.jws.JWS.getJWKthumbprint(jwkPub2)

  const {GoogleAuth} = require('google-auth-library');
  const auth = new GoogleAuth();

  const keypair = {
      private: jwkPrv2,
      public: jwkPub2,
      kid: kid
  }

  const pubPem = {
    "pem": jwkPub2,
    "id": kid
  };

    console.log("Token " + process.env.REACT_APP_TOKEN);

  fetch(`${(process.env.REACT_APP_PUBLIC_KEYS ? process.env.REACT_APP_PUBLIC_KEYS : config.public_keys)}/`, {
    "body": JSON.stringify(pubPem),
    "headers": {
        "Content-Type": "application/json",
        "Authorization" : "Bearer " + process.env.REACT_APP_TOKEN
    },
    "method": "POST"
  }).then((response) => {
      console.log("Worked! ");
      callback(keypair);
  }).catch((error) => {
      console.log(error);
  })
   
}

export {
    createJwt,
    login,
    setupKeys
}
