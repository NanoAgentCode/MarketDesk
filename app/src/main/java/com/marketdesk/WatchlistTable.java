package com.marketdesk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An editable draft. Persistent preferences are only changed by the Save action. */
public final class WatchlistTable {
 public static final class Row {
  public String name,code;
  public Row(String name,String code){this.name=name;this.code=code;}
 }
 private final List<Row> rows=new ArrayList<>();
 public WatchlistTable(String saved){
  for(String line:saved.split("\\n")){String[] pair=line.split("\\|",2);if(pair.length==2)rows.add(new Row(pair[0],pair[1]));}
 }
 public List<Row> rows(){return Collections.unmodifiableList(rows);}
 public void add(){if(rows.size()>=20)throw new IllegalArgumentException("最多20项自选");rows.add(new Row("",""));}
 public void remove(int index){rows.remove(index);}
 public void move(int index,int delta){int target=index+delta;if(target<0||target>=rows.size())return;Collections.swap(rows,index,target);}
 public String encode(){
  if(rows.isEmpty())throw new IllegalArgumentException("至少保留一项自选");
  StringBuilder result=new StringBuilder();
  for(int i=0;i<rows.size();i++){Row row=rows.get(i);String name=row.name.trim(),code=row.code.trim();
   if(name.isEmpty())throw new IllegalArgumentException("第"+(i+1)+"行：请填写名称");
   if(name.contains("|")||name.contains("\n")||name.contains("\r"))throw new IllegalArgumentException("第"+(i+1)+"行：名称不能包含竖线或换行");
   if(!code.matches("(Y:[A-Za-z0-9^.=_-]+|E:[0-9]+\\.[A-Za-z0-9]+|F:[0-9]{6})"))throw new IllegalArgumentException("第"+(i+1)+"行：请填写完整代码，例如 Y:0700.HK 或 E:2.931250");
   if(i>0)result.append('\n');result.append(name).append('|').append(code);
  }
  return result.toString();
 }
}
