export const administrator='himentpear@gmail.com';
export function validateOrganization(org, expectedDomain) {
  if (!/^[a-z0-9-]+\.cloudflareaccess\.com$/.test(org?.auth_domain||'')) throw new Error('Access organization must be initialized in Zero Trust');
  if (expectedDomain && expectedDomain!==org.auth_domain) throw new Error('Access team domain does not match this account');
  return 'https://'+org.auth_domain;
}
export function validatePolicies(policies) {
  // Existing broader allow/bypass/service-token policies must be reviewed rather than silently adopted.
  for (const policy of policies) {
    if (policy.decision==='deny') continue;
    if (policy.decision!=='allow' || policy.include?.length!==1 ||
      policy.include[0]?.email?.email!==administrator ||
      (policy.exclude?.length||0) || (policy.require?.length||0))
      throw new Error('Existing Access policy is not the exact WeRus administrator allowlist; review it in Zero Trust');
  }
}
