package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import io.netty.buffer.Unpooled;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.network.*;
import xtdpotato.xero_quest_journal.core.runtime.*;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

final class GraphSearchPickupRegression {
    private static final Path OUT=Path.of("..","build","verification");
    private static int stage,wait;
    private static CompletableFuture<?> work;
    private static QuestUnlockGraphScreen graph;
    private static QuestScreen journal;
    private static long searchStarted,searchInputNanos,firstEntryStarted;
    private static double edgePan;
    private static double[] releasedViewport;
    private static final String GROUP="graph_search_group",ACQUIRE="notice_acquire",
            UNTRACKED_ACQUIRE="notice_untracked_acquire",SUBMIT="notice_submit";
    static boolean tick(Minecraft game)throws Exception {
        if(--wait>0)return false;
        if(game.screen instanceof AnimatedQuestScreen a && a.inputBlocked())return false;
        if(work!=null){if(!work.isDone())return false;work.join();work=null;}
        var server=game.getSingleplayerServer();var uuid=game.player.getUUID();
        switch(stage) {
            case 0 -> {
                game.setScreen(null);ClientPreferences.get().reducedMotion=true;ClientEditMode.set(true);
                work=server.submit(()->{
                    try{
                        var root=QuestRepository.root().resolve(GROUP);var gson=new com.google.gson.Gson();
                        AtomicFiles.write(root.resolve("group_manifest.json"),gson.toJson(Map.of("id",GROUP,"name","关联任务组","type","world","icon","minecraft:book")),false);
                        var ids=new ArrayList<String>();
                        for(int i=0;i<2000;i++){
                            String id="graphfix_"+i;ids.add(id);
                            AtomicFiles.write(root.resolve(id+".json"),gson.toJson(Map.of("id",id,"title","目标任务 "+i,"type","world","icon","minecraft:paper","defaultUnlocked",true,"triggers",List.of(Map.of("type","confirm","item","","count",1)))),false);
                        }
                        AtomicFiles.write(root.resolve("search.chapter.json"),gson.toJson(Map.of("id","graph_search_chapter","name","搜索测试章节","group",GROUP,"quests",ids)),false);
                        for(String id:List.of(ACQUIRE,UNTRACKED_ACQUIRE,SUBMIT)) {
                            String item = id.equals(UNTRACKED_ACQUIRE) ? "minecraft:diamond" : "minecraft:oak_log";
                            String type = id.equals(SUBMIT) ? "item_submit" : "item_acquire";
                            int count = id.equals(ACQUIRE) ? 80 : id.equals(UNTRACKED_ACQUIRE) ? 2 : 16;
                            AtomicFiles.write(QuestRepository.root().resolve(id+".json"),gson.toJson(Map.of(
                                    "id",id,"title",id.equals(ACQUIRE)?"获取原木":id.equals(UNTRACKED_ACQUIRE)?"获取钻石":"提交原木",
                                    "type","world","trackable",true,"defaultUnlocked",true,"rewardMode","manual",
                                    "triggers",List.of(Map.of("type",type,"item",item,"count",count)))),false);
                        }
                        QuestRepository.reload();check(QuestRepository.errors().isEmpty(),"Invalid graph fixtures");return true;
                    }catch(Exception e){throw new RuntimeException(e);}
                });
            }
            case 1 -> {
                ClientPreferences.get().collapsedJournalGroups=List.of(GROUP);
                journal=QuestScreen.task(null,ACQUIRE);journal.init(game,game.getWindow().getGuiScaledWidth(),game.getWindow().getGuiScaledHeight());journal.removed();
                check(((Set<?>)field(journal,"collapsedGroupIds")).contains(GROUP),"Journal fixture did not retain original folding");
                graph=new QuestUnlockGraphScreen(journal);game.setScreen(graph);button(graph,"unlock-task-list").onPress();wait=3;
            }
            case 2 -> {
                var rows=(JournalListModel)invoke(graph,"drawerRows",new Class<?>[]{});
                check(rows.rows().stream().anyMatch(r->r.group()!=null && r.group().id().equals(GROUP)),"Drawer lacks journal group");
                check(rows.rows().stream().noneMatch(r->r.quest()!=null && r.quest().id().equals("graphfix_0")),"Drawer ignored saved collapse state");
                invoke(graph,"toggleDrawerGroup",new Class<?>[]{String.class},GROUP);
                check(!ClientPreferences.get().collapsedJournalGroups.contains(GROUP),"Graph collapse state not shared");
                journal.init(game,graph.width,graph.height);
                check(!((Set<?>)field(journal,"collapsedGroupIds")).contains(GROUP),"Existing journal did not inherit graph expansion");journal.removed();
                var search=(EditBox)field(graph,"search");long started=System.nanoTime();
                search.setValue("目标");search.setValue("graphfix_1");search.setValue("mubiaorenwu 1999");searchInputNanos=System.nanoTime()-started;
                searchStarted=System.nanoTime();wait=3;
            }
            case 3 -> {
                if((boolean)field(graph,"searchPending")){check(System.nanoTime()-searchStarted<20_000_000_000L,"Async graph search timeout");return false;}
                check(((Set<?>)field(graph,"matchingTasks")).equals(Set.of("graphfix_1999")),"Stale or incorrect pinyin search result");
                check(((String)field(graph,"searchWorkerThread")).startsWith("Xero Quest Search-"),"Graph search ran on render thread");
                capture(game,"unlock-async-group-search-zh-1920");
                ((EditBox)field(graph,"search")).setValue("");invoke(graph,"selectTask",new Class<?>[]{String.class},"graphfix_1999");
                check(((Map<?,?>)field(graph,"groupLabels")).get("graphfix_1999").equals("关联任务组"),"Footer group absent");
                check(((Map<?,?>)field(graph,"chapterLabels")).get("graphfix_1999").equals("搜索测试章节"),"Footer chapter absent");
                double mx=(int)invoke(graph,"mapX",new Class<?>[]{})+(int)invoke(graph,"mapWidth",new Class<?>[]{})-2;
                double my=(int)invoke(graph,"mapY",new Class<?>[]{})+(int)invoke(graph,"mapContentHeight",new Class<?>[]{})/2.;
                graph.mouseClicked(mx,my,0);edgePan=(double)field(graph,"panX");wait=8;
            }
            case 4 -> {
                check(Math.abs((double)field(graph,"panX")-edgePan)>1,"Minimap edge stopped instead of auto-panning");
                check(!(boolean)field(graph,"minimapTooltipVisible"),"Tooltip displayed during minimap drag");
                double mx=(int)invoke(graph,"mapX",new Class<?>[]{})+(int)invoke(graph,"mapWidth",new Class<?>[]{});
                double my=(int)invoke(graph,"mapY",new Class<?>[]{})+10;
                graph.mouseDragged(mx+20,my,0,20,0);double a=(double)field(graph,"panX");
                graph.mouseDragged(mx+60,my,0,40,0);check(Math.abs((double)field(graph,"panX")-a)>1,"Minimap drag clamped at its border");
                releasedViewport=(double[])invoke(graph,"minimapViewport",new Class<?>[]{});
                graph.mouseReleased(mx+60,my,0);wait=5;
            }
            case 5 -> {
                var viewport=(double[])invoke(graph,"minimapViewport",new Class<?>[]{});
                for(int i=0;i<4;i++)check(Math.abs(viewport[i]-releasedViewport[i])<.001,"Minimap recentered on release");
                capture(game,"unlock-minimap-free-drag-zh-1920");
                game.setScreen(null);ItemPickupToast.clear();ClientPreferences.get().reducedMotion=false;
                ClientPreferences.get().itemPickupToastScalePercent=100;ClientPreferences.get().itemPickupToastTextScalePercent=100;
                work=server.submit(()->{
                    try{
                        var p=server.getPlayerList().getPlayer(uuid);p.getInventory().clearContent();p.getInventory().add(new ItemStack(Items.OAK_LOG,2));
                        QuestProgressData.get(server).trackOnly(uuid,ACQUIRE);
                        invoke(QuestConditionEvents.class,"acquired",new Class<?>[]{net.minecraft.server.level.ServerPlayer.class,ItemStack.class,int.class},p,new ItemStack(Items.DIAMOND),1);
                        invoke(QuestConditionEvents.class,"acquired",new Class<?>[]{net.minecraft.server.level.ServerPlayer.class,ItemStack.class,int.class},p,new ItemStack(Items.OAK_LOG),2);
                        return true;
                    }catch(Exception e){throw new RuntimeException(e);}
                });wait=2;
            }
            case 6 -> {
                if(ItemPickupToast.newest()==null)return false;
                check(ItemPickupToast.visibleCount()==2,"Tracked and untracked pickup objectives were not both notified");
                check(ItemPickupToast.newest().questId().equals(ACQUIRE) && ItemPickupToast.newest().current()==2,"Acquisition is not linked to task progress");
                check(((Deque<?>)field(ItemPickupToast.class,"entries")).stream().anyMatch(entry -> {
                    try {
                        var notice = (QuestNetwork.ItemPickupNoticePayload) field(entry,"notice");
                        return notice.questId().equals(UNTRACKED_ACQUIRE)
                                && notice.current()==1 && notice.required()==2;
                    } catch (Exception failure) { throw new IllegalStateException(failure); }
                }),"Untracked acquisition did not show its matching task quantity");
                var entries=(Deque<?>)field(ItemPickupToast.class,"entries");firstEntryStarted=(long)field(entries.peekFirst(),"startedAt");
                work=server.submit(()->{try{var p=server.getPlayerList().getPlayer(uuid);
                    invoke(QuestConditionEvents.class,"acquired",new Class<?>[]{net.minecraft.server.level.ServerPlayer.class,ItemStack.class,int.class},p,new ItemStack(Items.OAK_LOG),1);return true;
                }catch(Exception e){throw new RuntimeException(e);}});wait=2;
            }
            case 7 -> {
                if(ItemPickupToast.newest().current()!=3)return false;
                var entries=(Deque<?>)field(ItemPickupToast.class,"entries");
                check(entries.size()==3 && (long)field(entries.peekFirst(),"startedAt")>firstEntryStarted,"New item notice reused old animation clock");wait=7;
            }
            case 8 -> {
                capture(game,"compact-task-item-notices-zh-1920");
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);p.getInventory().clearContent();p.getInventory().add(new ItemStack(Items.OAK_LOG,15));
                    check(QuestRuntime.activate(p,SUBMIT).equals("insufficient"),"Insufficient submission succeeded");return true;});wait=2;
            }
            case 9 -> {
                check(ItemPickupToast.newest().action().equals("acquire"),"Failed submission created success notice");
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(uuid);p.getInventory().clearContent();p.getInventory().add(new ItemStack(Items.OAK_LOG,16));
                    check(QuestRuntime.activate(p,SUBMIT).equals("completed"),"Submission did not complete");
                    check(p.getInventory().countItem(Items.OAK_LOG)==0,"Submission did not consume items");return true;});wait=3;
            }
            case 10 -> {
                if(!ItemPickupToast.newest().action().equals("submit"))return false;
                var notice=ItemPickupToast.newest();check(notice.questId().equals(SUBMIT) && notice.pickedCount()==16 && notice.current()==16,"Submission notice lacks actual task/count");
                var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),game.level.registryAccess());
                try{QuestNetwork.ItemPickupNoticePayload.STREAM_CODEC.encode(buffer,notice);var copy=QuestNetwork.ItemPickupNoticePayload.STREAM_CODEC.decode(buffer);
                    check(copy.questId().equals(SUBMIT) && copy.action().equals("submit") && copy.required()==16,"Notice metadata codec mismatch");}finally{buffer.release();}
                check(NoticeGeometry.pickup(960,100,100,game.font).width()<=320,"Pickup preview still too wide");wait=7;
            }
            case 11 -> {
                capture(game,"compact-task-submit-notices-zh-1920");ItemPickupToast.clear();
                Files.writeString(OUT.resolve("graph-search-pickup-result.txt"),"PASS: 2000-task immutable background/pinyin search, stale result rejection; journal row model, group icons/shared collapse; selected task group/chapter metadata; smaller footer; minimap continuous edge movement, no border clamp, no release jump, suppressed drag tooltip; compact task-linked acquisition and successful submission notices, unrelated/failed events excluded; per-entry animation clocks and submission codec. Search edit cost: "+searchInputNanos/1_000_000.+" ms.\n");
                Files.writeString(OUT.resolve("generation-template-result.txt"),Files.readString(OUT.resolve("generation-template-result.txt"))+Files.readString(OUT.resolve("graph-search-pickup-result.txt")));
                return true;
            }
        }
        stage++;return false;
    }
    private static StyledButton button(net.minecraft.client.gui.screens.Screen s,String id){return s.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast).filter(b->b.styleId().equals(id)).findFirst().orElseThrow();}
    private static Object field(Object o,String name)throws Exception{Class<?> type=o instanceof Class<?> c?c:o.getClass();for(Class<?> c=type;c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o instanceof Class<?>?null:o);}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
    private static Object invoke(Object o,String name,Class<?>[] types,Object...args)throws Exception{Class<?> c=o instanceof Class<?> t?t:o.getClass();var m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o instanceof Class<?>?null:o,args);}
    private static void capture(Minecraft g,String name)throws Exception{Files.createDirectories(OUT);try(var i=Screenshot.takeScreenshot(g.getMainRenderTarget())){i.writeToFile(OUT.resolve(name+".png"));}}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message+" at graph/pickup stage "+stage);}
}
