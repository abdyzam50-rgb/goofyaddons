// Share only fixed failure categories; never forward upstream bodies or credentials.
export function profileFailure(status,body) {
 const text=typeof body?.error==='string'?body.error:'';
 let failureCode='PROFILE_SERVICE_UNAVAILABLE';
 if(/^Hypixel rejected profile access \(HTTP 403\)/.test(text))failureCode='HYPIXEL_FORBIDDEN';
 else if(/^Hypixel rejected profile access \(HTTP 401\)/.test(text))failureCode='HYPIXEL_UNAUTHORIZED';
 else if(status===503)failureCode='PROFILE_NOT_CONFIGURED';
 else if(status===429||/rate limited/.test(text))failureCode='PROFILE_RATE_LIMITED';
 else if(/^Username not found/.test(text))failureCode='USERNAME_NOT_FOUND';
 else if(/^(Minecraft|PlayerDB) username service/.test(text))failureCode='USERNAME_SERVICE_UNAVAILABLE';
 const errors={
  HYPIXEL_FORBIDDEN:'Hypixel rejected profile access (HTTP 403). Update the Worker HYPIXEL_API_KEY secret and check SkyBlock profile permissions.',
  HYPIXEL_UNAUTHORIZED:'Hypixel rejected profile access (HTTP 401). Update the Worker HYPIXEL_API_KEY secret.',
  PROFILE_NOT_CONFIGURED:'Profile lookup is not configured on the Worker; configure its API key and profile rate limiter.',
  PROFILE_RATE_LIMITED:'Profile lookup is rate limited; retry shortly.',
  USERNAME_NOT_FOUND:'Minecraft username could not be resolved.',
  USERNAME_SERVICE_UNAVAILABLE:'Minecraft username lookup service is unavailable; retry shortly.',
  PROFILE_SERVICE_UNAVAILABLE:'Profile lookup service is unavailable; check the Worker.'
 };
 return {failureCode,error:errors[failureCode]};
}
