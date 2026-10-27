export class ApiError extends Error {constructor(public status:number,message:string){super(message);}}
async function send(url:string,options:RequestInit):Promise<Response>{
 const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),15000);
 try{return await fetch(url,{...options,credentials:'include',signal:controller.signal});}
 catch(error){throw new Error(error instanceof DOMException&&error.name==='AbortError'?'응답이 지연되고 있습니다. 예약 요청이었다면 나의 예약에서 결과를 먼저 확인해 주세요.':'서버에 연결하지 못했습니다. 연결을 확인한 뒤 다시 시도해 주세요.');}
 finally{clearTimeout(timer);}
}
async function request<T>(path:string,method='GET',body?:unknown,retry=true):Promise<T>{
 const headers:Record<string,string>={};
 if(method!=='GET'){
  const csrf=await send('/api/csrf',{});
  if(!csrf.ok)throw new ApiError(csrf.status,'요청 인증 정보를 가져오지 못했습니다.');
  headers['X-XSRF-TOKEN']=(await csrf.json()).token;
 }
 if(body!==undefined)headers['Content-Type']='application/json';
 const response=await send('/api'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});
 if(!response.ok){
  const data=await response.json().catch(()=>({}));
  if(response.status===403&&retry&&data.message==='요청 인증이 만료되었습니다. 다시 시도해 주세요.')return request<T>(path,method,body,false);
  if(response.status===401&&!path.startsWith('/auth/'))window.dispatchEvent(new Event('spacepick:session-expired'));
  throw new ApiError(response.status,data.message||'요청을 처리하지 못했습니다.');
 }
 return response.status===204?undefined as T:response.json();
}
export const api={get:<T>(p:string)=>request<T>(p),post:<T>(p:string,b?:unknown)=>request<T>(p,'POST',b)};
export type Profile={id:number;name:string;email:string;role:string};
export type Space={id:number;name:string;district:string;address:string;kind:string;capacity:number;hourlyPrice:number;imageUrl:string;description:string;amenities:string[];rules:string;openHour:number;closeHour:number};
export type Booking={id:number;spaceId:number;spaceName:string;imageUrl:string;district:string;memberName:string;startsAt:string;endsAt:string;guests:number;totalPrice:number;status:string;paymentOrderId:string|null};
export const money=(n:number)=>n.toLocaleString('ko-KR');
