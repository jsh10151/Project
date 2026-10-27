import {useEffect,useState} from 'react';
import {api} from './api';
import {cleanPaymentUrl} from './navigation';
export type PaymentOrder={orderId:string;bookingId:number;amount:number;state:string;customerKey:string;clientKey:string};
let sdk:Promise<void>|undefined;
export async function openCheckout(order:PaymentOrder){
 if(!order.clientKey.startsWith('test_ck_'))throw new Error('토스 테스트 클라이언트 키가 필요합니다.');
 if(order.state!=='PENDING')throw new Error('새 결제창을 열 수 없는 주문입니다. 나의 예약에서 상태를 확인해 주세요.');
 sdk??=new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='https://js.tosspayments.com/v2/standard';script.onload=()=>resolve();script.onerror=()=>{sdk=undefined;script.remove();reject(new Error('결제창을 불러오지 못했습니다. 나의 예약에서 대기 주문을 확인해 주세요.'));};document.head.appendChild(script);});
 await sdk;
 const toss=(window as unknown as {TossPayments:(key:string)=>{payment:(options:{customerKey:string})=>{requestPayment:(options:unknown)=>Promise<void>}}}).TossPayments(order.clientKey);
 await toss.payment({customerKey:order.customerKey}).requestPayment({method:'CARD',amount:{currency:'KRW',value:order.amount},orderId:order.orderId,orderName:'SPACE PICK 공간 예약',successUrl:location.origin+'/payment/success',failUrl:location.origin+'/payment/fail?orderId='+encodeURIComponent(order.orderId)});
}
export function PaymentResult(){
 const [state,setState]=useState('');const [error,setError]=useState('');const [busy,setBusy]=useState(false);
 const [params]=useState(()=>new URLSearchParams(location.search));const failed=location.pathname.endsWith('/fail');
 async function confirm(){const orderId=params.get('orderId'),paymentKey=params.get('paymentKey'),amount=Number(params.get('amount'));if(!orderId){setError('결제 주문 정보가 없습니다. 나의 예약에서 확인해 주세요.');return;}setBusy(true);setError('');try{let result:PaymentOrder;if(paymentKey&&Number.isSafeInteger(amount)&&amount>0){result=await api.post<PaymentOrder>('/payments/confirm',{orderId,paymentKey,amount});}else{result=await api.get<PaymentOrder>('/payments/orders/'+encodeURIComponent(orderId));if(result.state==='CONFIRMING')result=await api.post<PaymentOrder>('/payments/orders/'+encodeURIComponent(orderId)+'/reconcile');if(result.state==='PENDING')throw new Error('승인 요청이 완료되지 않았습니다. 나의 예약에서 상태를 확인해 주세요.');}setState(result.state);}catch(e){setError(e instanceof Error?e.message:'승인 결과 확인 중입니다.');}finally{setBusy(false);}}
 useEffect(()=>{const clean=cleanPaymentUrl(new URL(location.href));if(clean)history.replaceState(null,'',clean);if(!failed)void confirm();},[]);
 return <section className="page confirmation"><span className="eyebrow dark">TOSS TEST PAYMENT</span><h1>{failed?'결제를 완료하지 못했어요.':state==='PAID'?'결제 승인과 예약이 확정됐어요.':state==='REFUNDED'?'환불과 예약 취소가 완료됐어요.':state==='CANCELLED'||state==='EXPIRED'?'취소되었거나 만료된 주문입니다.':error?'결제 결과 확인이 필요해요.':'결제 결과를 확인하고 있어요.'}</h1><p>{failed?'예약은 결제 대기 상태이며 15분 후 점유가 해제됩니다. 나의 예약에서 바로 취소할 수도 있습니다.':'서버 주문 금액과 토스 승인 응답을 확인합니다.'}</p>{error&&<p className="form-error" role="alert">{error}</p>}{!failed&&!['PAID','REFUNDED','CANCELLED','EXPIRED'].includes(state)&&<button className="button green" disabled={busy} onClick={()=>void confirm()}>{busy?'확인 중…':'같은 주문 결과 다시 확인'}</button>}<a className="button outline" href="#/my">나의 예약으로</a></section>;
}