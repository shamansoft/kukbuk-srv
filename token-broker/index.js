// Cloud Function code (Node.js)
const {GoogleAuth} = require('google-auth-library');
const auth = new GoogleAuth();

const targetAudience = 'https://cookbook-577683305271.us-west1.run.app';


exports.getIdToken = async (req, res) => {
  // Set CORS headers
  res.set('Access-Control-Allow-Origin', '*');
  console.log("req.headers", req.headers);
  // Get the OAuth token from the request header
  const oauthToken = req.headers.authorization?.split(' ')[1];
  if (!oauthToken) {
    return res.status(401).json({error: 'No token provided'});
  }

  console.log("oauthToken", oauthToken);
  try {
    // Create OAuth client and set credentials

    const client = await auth.getIdTokenClient(targetAudience);
    const token = await client.idTokenProvider.fetchIdToken(targetAudience);
    console.log("token", token);
    res.json({id_token: token});
  } catch (error) {
    console.error('Error getting ID token:', error);
    res.status(400).json({error: error.message});
  }
};