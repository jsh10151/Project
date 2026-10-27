// Keep payment callback credentials only in memory, not in subsequent navigation URLs.
export function cleanPaymentUrl(url:URL):string|null {
 if(!/^\/payment\/(success|fail)$/.test(url.pathname))return null;
 if(url.hash&&url.hash!=='#/payment-result')return '/'+url.hash;
 const order=url.searchParams.get('orderId');
 return url.pathname+(order?'?orderId='+encodeURIComponent(order):'')+'#/payment-result';
}