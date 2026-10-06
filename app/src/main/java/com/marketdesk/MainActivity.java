package com.marketdesk;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

public class MainActivity extends Activity {
 private static final int BG=0xff0c1422, CARD=0xff152133, TEXT=0xffeef4fc,
  MUTED=0xff94a6bd, ACCENT=0xff8ae3ce, LINE=0xff25354b, RED=0xffff7b87, GREEN=0xff62d6aa;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private LinearLayout content, tabs;
 private TextView pinStatus, liveStatus;
 private int page;
 private boolean resumed, helpShown;
 private long requestToken, requestedAt;
 private int[] priorWidgetIds=new int[0];
 private String quoteSnapshot="";
 private ScrollView pageScroll;
 private Dialog editorDialog;
 private WatchlistTable editorDraft;
 private FrameLayout widgetPreviewHost;
 private TextView widgetPreviewInfo;
 private int previewWidgetId;
 private String widgetPreviewSignature="";
 private final Runnable syncDisplayedQuotes=()->{
  if(!resumed)return;if(page==0&&editorDialog==null)renderQuotes();if(page==1)refreshWidgetPreview(false);
 };
 private final SharedPreferences.OnSharedPreferenceChangeListener quoteListener=(preferences,key)->{
  if("attempt".equals(key)||"watch".equals(key)){handler.removeCallbacks(syncDisplayedQuotes);handler.post(syncDisplayedQuotes);}
 };
 private final List<EditCell> editorCells=new ArrayList<>();
 private static final class EditCell {
  final WatchlistTable.Row row;final EditText name,code;
  EditCell(WatchlistTable.Row row,EditText name,EditText code){this.row=row;this.name=name;this.code=code;}
 }
 private final Runnable tick=new Runnable(){ public void run(){
  if(!resumed) return;
  if(page==0 && editorDialog==null && !quoteSnapshot.equals(snapshot())) renderQuotes();
  if(page==1){refreshWidgetPreview(false);updatePinStatus();}
  if(page==2 && liveStatus!=null) liveStatus.setText(liveDescription());
  handler.postDelayed(this,2500);
 }};

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  if(saved!=null){page=saved.getInt("page");requestToken=saved.getLong("pinToken");requestedAt=saved.getLong("pinAt");helpShown=saved.getBoolean("pinHelp");int[] ids=saved.getIntArray("priorIds");if(ids!=null)priorWidgetIds=ids;}
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
  LinearLayout root=column();root.setBackgroundColor(BG);
  root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(20),insets.getSystemWindowInsetTop()+dp(12),dp(20),insets.getSystemWindowInsetBottom());return insets;});
  setContentView(root);root.requestApplyInsets();
  LinearLayout header=row();header.setPadding(0,dp(8),0,dp(18));
  LinearLayout brand=column();label(brand,"MARKET DESK",10,ACCENT,true);label(brand,"行情桌面",26,TEXT,true);
  header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  TextView badge=label(null,"自选行情",11,MUTED,false);badge.setPadding(dp(12),dp(8),dp(12),dp(8));badge.setBackground(bg(CARD,12));header.addView(badge);root.addView(header);
  tabs=row();tabs.setPadding(dp(4),dp(4),dp(4),dp(4));tabs.setBackground(bg(CARD,16));root.addView(tabs);
  pageScroll=new ScrollView(this);pageScroll.setFillViewport(true);pageScroll.setClipToPadding(false);pageScroll.setPadding(0,dp(20),0,dp(20));
  content=column();pageScroll.addView(content);root.addView(pageScroll,new LinearLayout.LayoutParams(-1,0,1));
  switchPage(page);RefreshWorker.schedule(this);RefreshWorker.now(this);
  if(saved!=null&&saved.containsKey("draftNames")){editorDraft=new WatchlistTable("");ArrayList<String> names=saved.getStringArrayList("draftNames"),codes=saved.getStringArrayList("draftCodes");if(names!=null&&codes!=null)for(int i=0;i<Math.min(names.size(),codes.size());i++){editorDraft.add();WatchlistTable.Row row=editorDraft.rows().get(i);row.name=names.get(i);row.code=codes.get(i);}editWatchlist();}
 }

 private void switchPage(int selected){
  if(pageScroll!=null)pageScroll.scrollTo(0,0);
  page=selected;tabs.removeAllViews();String[] names={"自选","桌面","设置"};
  for(int i=0;i<names.length;i++){final int target=i;TextView tab=label(null,names[i],14,i==page?BG:MUTED,i==page);
   tab.setGravity(Gravity.CENTER);tab.setPadding(dp(4),dp(12),dp(4),dp(12));if(i==page)tab.setBackground(bg(ACCENT,12));
   tabs.addView(tab,new LinearLayout.LayoutParams(0,-2,1));tab.setOnClickListener(v->switchPage(target));}
  pinStatus=null;liveStatus=null;widgetPreviewHost=null;widgetPreviewInfo=null;
  if(page==0)renderQuotes();else if(page==1)renderWidget();else renderSettings();
 }

 private void renderQuotes(){
  quoteSnapshot=snapshot();
  int previousScroll=pageScroll.getScrollY();
  content.removeAllViews();List<String[]> items=Quotes.items(this);int ready=0;
  for(String[] item:items)if(Quotes.cached(this,item[1]).has("price"))ready++;
  label(content,"我的自选",20,TEXT,true);label(content,items.size()+" 项关注 · "+ready+" 项已有行情 · 后台约15分钟更新",12,MUTED,false);
  LinearLayout tools=row();halfAction(tools,"↻  刷新",true,()->{RefreshWorker.now(this);toast("已安排刷新，有网络时执行");});halfAction(tools,"编辑表格",false,()->editWatchlist());content.addView(tools);
  long attempt=prefs().getLong("attempt",0);label(content,attempt==0?"正在获取行情…":"上次刷新尝试 "+Quotes.beijing(attempt)+" · 北京时间",11,MUTED,false);gap(content,8);
  LinearLayout table=column();table.setBackground(bg(CARD,16));
  LinearLayout header=row();header.setBackgroundColor(LINE);
  quoteCell(header,"名称 / 代码",1.5f,Gravity.START,MUTED,true);quoteCell(header,"涨跌幅",0.9f,Gravity.END,MUTED,true);quoteCell(header,"行情时间",1.1f,Gravity.CENTER,MUTED,true);tableCell(header,"更多",48,Gravity.CENTER,MUTED,true);table.addView(header);
  int index=0;for(String[] item:items){JSONObject q=Quotes.cached(this,item[1]);LinearLayout line=row();line.setMinimumHeight(dp(64));line.setBackgroundColor(index++%2==0?CARD:0xff18263b);
   LinearLayout name=column();name.setPadding(dp(6),dp(8),dp(4),dp(8));TextView title=label(name,item[0],13,TEXT,true);title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(0,0,0,0);title.setLineSpacing(dp(1),1);TextView code=label(name,item[1],10,MUTED,false);code.setMaxLines(2);code.setEllipsize(android.text.TextUtils.TruncateAt.END);code.setPadding(0,dp(3),0,0);code.setLineSpacing(0,1);line.addView(name,new LinearLayout.LayoutParams(0,-2,1.5f));
   quoteCell(line,q.has("change")?String.format(Locale.CHINA,"%+.2f%%",q.optDouble("change")):"—",0.9f,Gravity.END,q.has("change")?changeColor(q):MUTED,true);
   quoteCell(line,q.has("price")?q.optString("time").replace(" ","\n"):"—",1.1f,Gravity.CENTER,MUTED,false);
   TextView more=label(null,"更多",12,ACCENT,true);more.setGravity(Gravity.CENTER);more.setMinHeight(dp(48));more.setContentDescription("查看"+item[0]+"的最新价和数据状态");more.setOnClickListener(v->showQuoteDetails(item));line.addView(more,new LinearLayout.LayoutParams(dp(48),dp(64)));
   line.setOnClickListener(v->showQuoteDetails(item));table.addView(line);divider(table);
  }
  content.addView(table,new LinearLayout.LayoutParams(-1,-2));gap(content,6);label(content,"点击「更多」查看最新价和数据状态\n红涨绿跌 · 基金为估算净值",11,MUTED,false);
  pageScroll.post(()->{if(page==0)pageScroll.scrollTo(0,previousScroll);});
 }

 private void showQuoteDetails(String[] item){
  JSONObject q=Quotes.cached(this,item[1]);
  String price=q.has("price")?String.format(Locale.CHINA,"%,.3f",q.optDouble("price")):"暂无行情";
  String change=q.has("change")?String.format(Locale.CHINA,"%+.2f%%",q.optDouble("change")):"—";
  String status=!q.has("price")?prefs().getString("error:"+item[1],"等待行情"):q.optString("source")+(Quotes.historical(q)?" · 历史行情":"")+(prefs().contains("error:"+item[1])?"\n"+prefs().getString("error:"+item[1],"")+"，保留缓存":System.currentTimeMillis()-q.optLong("received")>20*60000?" · 缓存待更新":"");
  String message="行情代码："+item[1]+"\n\n最新价："+price+(q.optString("unit").isEmpty()?"":" "+q.optString("unit"))+"\n涨跌幅："+change+"\n行情时间："+q.optString("time","—")+"（北京时间）\n\n数据状态："+status+"\n\n涨跌幅以上一交易日收盘价为基准。行情可能延迟。";
  new AlertDialog.Builder(this).setTitle(item[0]).setMessage(message).setPositiveButton("关闭",null).show();
 }

 private void renderWidget(){
  content.removeAllViews();label(content,"把关注，放在桌面",24,TEXT,true);label(content,"随手看涨跌，不用每次打开 App。",13,MUTED,false);gap(content,18);
  widgetPreviewInfo=label(content,"",12,MUTED,false);widgetPreviewInfo.setOnClickListener(v->choosePreviewWidget());
  widgetPreviewHost=new FrameLayout(this);content.addView(widgetPreviewHost,new LinearLayout.LayoutParams(-1,dp(280)));refreshWidgetPreview(true);
  label(content,"预览与小部件共用布局及行情，较大尺寸会缩放。点击上方可切换小部件。",11,MUTED,false);gap(content,20);
  action(content,"编辑名称 / 代码",false,()->editWatchlist());
  action(content,"＋  添加到桌面",true,()->requestWidget());pinStatus=label(content,"",12,MUTED,false);updatePinStatus();gap(content,16);
  LinearLayout help=card();label(help,"没有弹出添加窗口？",16,TEXT,true);label(help,"桌面由系统处理添加请求。可以直接在手机桌面手动选择小部件。",12,MUTED,false);gap(help,8);
  label(help,"01  回到桌面，长按空白位置或双指捏合\n02  打开「小部件 / 添加小部件」\n03  找到「行情桌面」，拖到空白区域",13,TEXT,false);
  label(help,"HyperOS 入口随版本不同；部分版本需进入「全部小部件 / 安卓小部件」列表。",11,MUTED,false);
  action(help,"查看添加指引",false,()->manualHelp());content.addView(help);
 }

 private void choosePreviewWidget(){int[] ids=widgetIds();if(ids.length==0){toast("添加小部件后，将按实际尺寸预览");return;}String[] names=new String[ids.length];for(int i=0;i<ids.length;i++){Bundle b=AppWidgetManager.getInstance(this).getAppWidgetOptions(ids[i]);names[i]="小部件 #"+ids[i]+" · "+b.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300)+" × "+b.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,280);}
  new AlertDialog.Builder(this).setTitle("选择桌面小部件").setItems(names,(d,index)->{previewWidgetId=ids[index];refreshWidgetPreview(true);}).setNegativeButton("取消",null).show();
 }
 private void refreshWidgetPreview(boolean force){
  if(widgetPreviewHost==null||widgetPreviewInfo==null)return;int[] ids=widgetIds();boolean found=false;for(int id:ids)if(id==previewWidgetId)found=true;
  if(!found)previewWidgetId=ids.length>0?ids[0]:0;
  Bundle options=previewWidgetId==0?new Bundle():AppWidgetManager.getInstance(this).getAppWidgetOptions(previewWidgetId);
  int width=Math.max(180,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300)),height=Math.max(120,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,280));
  String signature=snapshot()+"/"+previewWidgetId+"/"+width+"/"+height;
  if(!force&&signature.equals(widgetPreviewSignature))return;widgetPreviewSignature=signature;
  MarketWidget.DisplaySnapshot batch=MarketWidget.snapshot(this);MarketWidget.render(this,batch);
  int count=WidgetPresentation.rows(batch.values,height,batch.time).size();
  widgetPreviewInfo.setText((previewWidgetId==0?"默认尺寸预览（尚未添加）":"小部件 #"+previewWidgetId)+" · "+width+" × "+height+" · 显示 "+count+" 项");
  widgetPreviewHost.removeAllViews();View rendered=MarketWidget.views(this,height,batch).apply(this,widgetPreviewHost);
  int available=getResources().getDisplayMetrics().widthPixels-dp(40);float scale=Math.min(1f,(float)Math.max(dp(180),available)/dp(width));
  rendered.setPivotX(0);rendered.setPivotY(0);rendered.setScaleX(scale);rendered.setScaleY(scale);widgetPreviewHost.addView(rendered,new FrameLayout.LayoutParams(dp(width),dp(height)));
  widgetPreviewHost.getLayoutParams().height=Math.round(dp(height)*scale);widgetPreviewHost.requestLayout();
  View refresh=rendered.findViewById(R.id.refresh);refresh.setOnClickListener(v->{RefreshWorker.now(this);toast("已安排刷新，将同步更新预览和小部件");});
  rendered.findViewById(R.id.title).setOnClickListener(v->switchPage(0));
 }

 private void requestWidget(){
  AppWidgetManager manager=AppWidgetManager.getInstance(this);
  if(!manager.isRequestPinAppWidgetSupported()){manualHelp();pinStatus.setText("当前桌面不支持 App 内添加，请使用手动添加。");return;}
  if(requestToken!=0 && System.currentTimeMillis()-requestedAt<10000){toast("已请求系统桌面，请稍候或使用手动添加");return;}
  priorWidgetIds=widgetIds();requestToken=System.currentTimeMillis();requestedAt=requestToken;helpShown=false;
  Intent done=new Intent(this,WidgetPinnedReceiver.class).setAction("com.marketdesk.PINNED").setData(Uri.parse("marketdesk://pin/"+requestToken)).putExtra("request_token",requestToken);
  PendingIntent callback=PendingIntent.getBroadcast(this,30,done,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  try{
   boolean accepted=manager.requestPinAppWidget(new ComponentName(this,MarketWidget.class),null,callback);
   if(!accepted){requestToken=0;pinStatus.setText("桌面未接受添加请求，请手动添加。");manualHelp();return;}
   pinStatus.setText("已请求系统桌面，请在弹出的窗口中确认添加。");toast("已请求桌面，未弹窗时可使用下方添加指引");handler.postDelayed(this::updatePinStatus,10000);
  }catch(RuntimeException error){requestToken=0;pinStatus.setText("未能唤起系统桌面，请使用手动添加。");manualHelp();}
 }

 private void updatePinStatus(){
  if(pinStatus==null)return;int[] current=widgetIds();
  if(requestToken!=0){boolean success=prefs().getLong("pin_success",0)==requestToken || PinFeedback.hasNewWidget(priorWidgetIds,current);
   PinFeedback.State state=PinFeedback.resolve(true,success,System.currentTimeMillis()-requestedAt);
   if(state==PinFeedback.State.CONFIRMED){requestToken=0;pinStatus.setText("已添加到桌面 · 当前有 "+current.length+" 个小部件");toast("桌面小部件已添加");MarketWidget.render(this);return;}
   if(state==PinFeedback.State.WAITING){pinStatus.setText("等待桌面确认… 若未弹窗，可使用下方手动添加指引。");return;}
   pinStatus.setText("尚未收到添加确认。可回桌面检查，或按指引手动添加。");if(resumed && hasWindowFocus() && !helpShown){helpShown=true;manualHelp();}
  }else pinStatus.setText(current.length>0?"已检测到 "+current.length+" 个桌面小部件，可再添加一个。":"添加后可以拉伸大小，点击标题打开 App。");
 }

 private int[] widgetIds(){return AppWidgetManager.getInstance(this).getAppWidgetIds(new ComponentName(this,MarketWidget.class));}
 private void manualHelp(){new AlertDialog.Builder(this).setTitle("手动添加桌面小部件").setMessage("1. 回到手机桌面，长按空白处或双指捏合。\n\n2. 点击“小部件 / 添加小部件”，找到“行情桌面”。部分 HyperOS 版本需进入“全部小部件 / 安卓小部件”。\n\n3. 拖到有足够空间的位置，然后拉伸大小。\n\n如果列表里没有：检查是否为主空间安装的 App、是否使用系统桌面；可先重新打开 App，再查看小部件列表。桌面菜单以手机实际显示为准。")
  .setPositiveButton("回到桌面",(d,w)->{try{startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));}catch(RuntimeException e){toast("请按手机 Home 键回到桌面");}}).setNegativeButton("留在 App",null).show();}

 private void renderSettings(){
  content.removeAllViews();label(content,"按你的习惯看行情",24,TEXT,true);label(content,"自选、刷新和后台运行，在这里管理。",13,MUTED,false);gap(content,18);
  LinearLayout watch=card();label(watch,"自选管理",18,TEXT,true);label(watch,"当前关注 "+Quotes.items(this).size()+" 项，桌面与 App 同步。",12,MUTED,false);
  action(watch,"编辑自选列表",false,()->editWatchlist());action(watch,"添加常用标的",false,()->addPreset());content.addView(watch);gap(content,14);
  LinearLayout live=card();label(live,"盯盘模式",18,TEXT,true);liveStatus=label(live,liveDescription(),12,MUTED,false);
  action(live,"开启 60 秒盯盘",true,()->startLive());action(live,"结束盯盘",false,()->{stopService(new Intent(this,LiveService.class));liveStatus.setText("已结束盯盘 · 后台约15分钟更新");});
  label(live,"最长2小时，开启时显示常驻通知。系统省电和行情源延迟仍可能影响更新。",11,MUTED,false);content.addView(live);gap(content,14);
  LinearLayout system=card();label(system,"后台与通知",18,TEXT,true);label(system,"若 HyperOS 限制后台，可检查自启动、通知和应用省电设置。默认后台刷新约15分钟，系统可能延后。",12,MUTED,false);
  action(system,"打开应用系统设置",false,()->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(RuntimeException e){toast("请从手机设置中打开行情桌面的应用详情");}});content.addView(system);gap(content,16);
  label(content,"行情桌面  1.7.2\n行情来源：Yahoo / 东方财富 / 天天基金\n场外基金为估算，黄金期货非实物黄金报价。",11,MUTED,false);
 }

 private void startLive(){if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},7);return;}
  try{startForegroundService(new Intent(this,LiveService.class));toast("正在开启盯盘");}catch(RuntimeException e){toast("系统暂不允许开启盯盘，请稍后再试");}}
 @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==7){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startLive();else new AlertDialog.Builder(this).setTitle("通知未开启").setMessage("允许通知后，可以在通知栏看到盯盘运行状态并结束盯盘。普通刷新仍可使用。").setPositiveButton("知道了",null).show();}}
 private String liveDescription(){long end=prefs().getLong("live_until",0);return LiveService.running && end>System.currentTimeMillis()?"盯盘运行中 · 约 "+Math.max(1,(end-System.currentTimeMillis())/60000)+" 分钟后结束":"未开启 · 后台约15分钟更新";}

 private void editWatchlist(){
  if(editorDialog!=null)return;if(editorDraft==null)editorDraft=new WatchlistTable(Quotes.watch(this));
  Dialog dialog=new Dialog(this,R.style.AppTheme);editorDialog=dialog;
  LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(12),dp(20),dp(12),dp(12));root.setFocusableInTouchMode(true);root.requestFocus();
  label(root,"编辑自选表格",24,TEXT,true);label(root,"直接点击名称或代码修改。最多20项，保存后同步到桌面。",12,MUTED,false);
  label(root,"直接编辑单元格；右侧 ↑ 上移 / ↓ 下移 / × 删除",11,ACCENT,false);gap(root,12);
  ScrollView vertical=new ScrollView(this);vertical.setFillViewport(true);HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setFillViewport(true);horizontal.setHorizontalScrollBarEnabled(true);
  LinearLayout table=column();horizontal.addView(table);vertical.addView(horizontal);root.addView(vertical,new LinearLayout.LayoutParams(-1,0,1));
  TextView error=label(root,"",12,RED,false);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
  action(root,"＋  添加一行",false,()->{captureDraft();try{editorDraft.add();renderEditor(table,error);vertical.post(()->vertical.fullScroll(View.FOCUS_DOWN));}catch(IllegalArgumentException e){error.setText(e.getMessage());}});
  LinearLayout bottom=row();halfAction(bottom,"取消",false,dialog::cancel);halfAction(bottom,"保存表格",true,()->{captureDraft();try{String value=editorDraft.encode();saveWatch(value);dialog.dismiss();}catch(IllegalArgumentException e){error.setText(e.getMessage());}});root.addView(bottom);
  TextView examples=label(root,"查看代码示例",12,ACCENT,false);examples.setGravity(Gravity.CENTER);examples.setPadding(0,dp(12),0,dp(12));examples.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("代码示例").setMessage("港股：Y:0700.HK\n恒生科技：Y:HSTECH.HK\n港股通创新药：E:2.931250\n美股：Y:AAPL / Y:QQQ\nA股：E:1.600519 / E:0.000001\n黄金ETF：E:1.518880\n场外基金估算：F:000001").setPositiveButton("知道了",null).show());
  dialog.setContentView(root);dialog.setOnDismissListener(d->{editorDialog=null;editorDraft=null;editorCells.clear();});
  renderEditor(table,error);dialog.show();Window w=dialog.getWindow();if(w!=null){w.setLayout(-1,-1);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(12),insets.getSystemWindowInsetTop()+dp(12),dp(12),insets.getSystemWindowInsetBottom()+dp(12));return insets;});root.requestApplyInsets();}
 }
 private void captureDraft(){for(EditCell cell:editorCells){cell.row.name=cell.name.getText().toString();cell.row.code=cell.code.getText().toString();}}
 private void renderEditor(LinearLayout table,TextView error){
  table.removeAllViews();editorCells.clear();error.setText("");
  LinearLayout header=row();header.setBackgroundColor(LINE);tableCell(header,"#",28,Gravity.CENTER,MUTED,true);tableCell(header,"名称",104,Gravity.START,MUTED,true);tableCell(header,"行情代码",120,Gravity.START,MUTED,true);tableCell(header,"操作",96,Gravity.CENTER,MUTED,true);table.addView(header);
  int index=0;for(WatchlistTable.Row draft:editorDraft.rows()){final int position=index++;LinearLayout line=row();line.setBackgroundColor(position%2==0?CARD:0xff18263b);line.setMinimumHeight(dp(96));
   tableCell(line,String.valueOf(position+1),28,Gravity.CENTER,MUTED,false);
   EditText name=editorInput(draft.name,"名称",false),code=editorInput(draft.code,"Y:0700.HK",true);
   name.setContentDescription("第"+(position+1)+"行名称");code.setContentDescription("第"+(position+1)+"行行情代码");line.addView(name,new LinearLayout.LayoutParams(dp(104),-2));line.addView(code,new LinearLayout.LayoutParams(dp(120),-2));editorCells.add(new EditCell(draft,name,code));
   LinearLayout controls=row(),reorder=column();editorControl(reorder,"↑","上移第"+(position+1)+"行",position>0,()->{captureDraft();editorDraft.move(position,-1);renderEditor(table,error);});
   editorControl(reorder,"↓","下移第"+(position+1)+"行",position<editorDraft.rows().size()-1,()->{captureDraft();editorDraft.move(position,1);renderEditor(table,error);});controls.addView(reorder);
   editorControl(controls,"×","删除第"+(position+1)+"行",true,()->{captureDraft();editorDraft.remove(position);renderEditor(table,error);});line.addView(controls,new LinearLayout.LayoutParams(dp(96),-2));table.addView(line);divider(table);
  }
  if(editorDraft.rows().isEmpty())label(table,"暂无自选，点击下方「添加一行」。",14,MUTED,false);
 }
 private EditText editorInput(String value,String hint,boolean code){EditText input=new EditText(this);input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setTextSize(13);input.setPadding(dp(5),dp(10),dp(5),dp(10));input.setMinHeight(dp(64));input.setSelectAllOnFocus(false);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(code?android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS:0));input.setSingleLine(code);if(!code){input.setMaxLines(3);input.setHorizontallyScrolling(false);}input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);input.setHint(hint);input.setText(value);return input;}
 private void editorControl(LinearLayout parent,String value,String description,boolean enabled,Runnable task){TextView control=label(null,value,20,enabled?(value.equals("×")?RED:ACCENT):MUTED,true);control.setGravity(Gravity.CENTER);control.setContentDescription(description);control.setEnabled(enabled);control.setAlpha(enabled?1:0.3f);control.setMinHeight(dp(48));control.setOnClickListener(v->task.run());parent.addView(control,new LinearLayout.LayoutParams(dp(48),dp(48)));}
 private void addPreset(){String[] names={"黄金ETF（518880）","黄金期货（美元/盎司）","苹果","腾讯","标普500指数","沪深300指数","纳斯达克100ETF（QQQ）"};String[] codes={"E:1.518880","Y:GC=F","Y:AAPL","Y:0700.HK","Y:^GSPC","E:1.000300","Y:QQQ"};new AlertDialog.Builder(this).setTitle("添加常用标的").setItems(names,(d,index)->{for(String[] item:Quotes.items(this))if(item[1].equals(codes[index])){toast("已在自选列表中");return;}try{saveWatch(Quotes.watch(this).trim()+"\n"+names[index]+"|"+codes[index]);}catch(IllegalArgumentException e){toast(e.getMessage());}}).setNegativeButton("取消",null).show();}
 private void saveWatch(String value){Quotes.validate(value);prefs().edit().putString("watch",value).apply();MarketWidget.render(this);RefreshWorker.afterEdit(this);switchPage(page);toast("自选已保存，已同步到桌面小部件");}
 private String quoteStatus(String code,JSONObject q){if(!q.has("price"))return prefs().getString("error:"+code,"正在等待行情");boolean failed=prefs().contains("error:"+code);boolean stale=System.currentTimeMillis()-q.optLong("received")>20*60000;return q.optString("time")+" · "+q.optString("source")+(Quotes.historical(q)?" · 历史行情":"")+(failed?" · 更新失败，缓存":stale?" · 缓存待更新":"");}
 private String market(String code){if(code.startsWith("F:"))return "基金估算";if(code.equals("Y:GC=F"))return "黄金期货";if(code.endsWith(".HK")||code.equals("E:2.931250"))return "港股 / 指数";if(code.startsWith("E:")||code.endsWith(".SS")||code.endsWith(".SZ"))return "A股 / 指数 / ETF";return "美股 / 指数 / ETF";}
 private int changeColor(JSONObject q){return q.optDouble("change")>0?RED:q.optDouble("change")<0?GREEN:MUTED;}
 private int tint(int color){return Color.argb(24,Color.red(color),Color.green(color),Color.blue(color));}
 private SharedPreferences prefs(){return getSharedPreferences("market",0);}
 private String snapshot(){StringBuilder s=new StringBuilder(Quotes.watch(this));s.append(prefs().getLong("attempt",0));s.append(System.currentTimeMillis()/60000);for(String[] item:Quotes.items(this)){s.append(prefs().getString(item[1],""));s.append(prefs().getString("error:"+item[1],""));}return s.toString();}
 private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(18),dp(16),dp(18),dp(16));l.setBackground(bg(CARD,20));return l;}
 private TextView label(LinearLayout parent,String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);t.setFontFeatureSettings("tnum");t.setLineSpacing(dp(3),1);t.setPadding(0,dp(3),0,dp(3));if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));if(parent!=null)parent.addView(t);return t;}
 private void action(LinearLayout parent,String value,boolean primary,Runnable task){TextView button=label(null,value,14,primary?BG:ACCENT,true);button.setGravity(Gravity.CENTER);button.setMinHeight(dp(48));button.setPadding(dp(14),dp(12),dp(14),dp(12));button.setBackground(bg(primary?ACCENT:LINE,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);parent.addView(button,p);button.setOnClickListener(v->task.run());}
 private void metric(LinearLayout parent,String value,String caption){LinearLayout c=column();c.setPadding(0,dp(16),0,dp(12));label(c,value,24,TEXT,true);label(c,caption,10,MUTED,false);parent.addView(c,new LinearLayout.LayoutParams(0,-2,1));}
 private void halfAction(LinearLayout parent,String value,boolean primary,Runnable task){TextView button=label(null,value,14,primary?BG:ACCENT,true);button.setGravity(Gravity.CENTER);button.setMinHeight(dp(48));button.setBackground(bg(primary?ACCENT:LINE,12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),dp(10),dp(3),dp(10));parent.addView(button,p);button.setOnClickListener(v->task.run());}
 private void tableCell(LinearLayout parent,String value,int width,int gravity,int color,boolean bold){TextView cell=label(null,value,12,color,bold);cell.setGravity(gravity|Gravity.CENTER_VERTICAL);cell.setPadding(dp(4),dp(8),dp(4),dp(8));cell.setLineSpacing(dp(1),1);cell.setMinHeight(dp(40));cell.setMaxLines(3);cell.setEllipsize(android.text.TextUtils.TruncateAt.END);parent.addView(cell,new LinearLayout.LayoutParams(dp(width),-2));}
 private void quoteCell(LinearLayout parent,String value,float weight,int gravity,int color,boolean bold){TextView cell=label(null,value,12,color,bold);cell.setGravity(gravity|Gravity.CENTER_VERTICAL);cell.setPadding(dp(4),dp(8),dp(4),dp(8));cell.setLineSpacing(dp(1),1);cell.setMinHeight(dp(40));cell.setMaxLines(3);cell.setEllipsize(android.text.TextUtils.TruncateAt.END);parent.addView(cell,new LinearLayout.LayoutParams(0,-2,weight));}
 private void divider(LinearLayout parent){View line=new View(this);line.setBackgroundColor(LINE);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
 private void gap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
 private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
 @Override protected void onResume(){super.onResume();resumed=true;prefs().registerOnSharedPreferenceChangeListener(quoteListener);MarketWidget.render(this);handler.removeCallbacks(tick);handler.post(tick);}
 @Override protected void onPause(){super.onPause();resumed=false;prefs().unregisterOnSharedPreferenceChangeListener(quoteListener);handler.removeCallbacks(tick);handler.removeCallbacks(syncDisplayedQuotes);}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(editorDialog!=null)editorDialog.dismiss();super.onDestroy();}
 @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("page",page);out.putLong("pinToken",requestToken);out.putLong("pinAt",requestedAt);out.putBoolean("pinHelp",helpShown);out.putIntArray("priorIds",priorWidgetIds);if(editorDialog!=null){captureDraft();ArrayList<String> names=new ArrayList<>(),codes=new ArrayList<>();for(WatchlistTable.Row row:editorDraft.rows()){names.add(row.name);codes.add(row.code);}out.putStringArrayList("draftNames",names);out.putStringArrayList("draftCodes",codes);}}
}
