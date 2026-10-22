package xtdpotato.xero_quest_journal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.client.*;
import xtdpotato.xero_quest_journal.core.*;
import xtdpotato.xero_quest_journal.core.io.*;
import xtdpotato.xero_quest_journal.core.model.*;
import xtdpotato.xero_quest_journal.core.network.*;
import xtdpotato.xero_quest_journal.core.runtime.*;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;

final class GenerationRegression {
    private static final Path OUT = Path.of("..", "build", "verification");
    private static int stage, wait;
    private static CompletableFuture<?> work;
    private static QuestScreen journal;
    private static QuestTemplateScreen templates;
    private static Object stableButton;
    private static Map<String, String> sources;
    private static String village;
    private static HudNotificationLayoutScreen hud;
    private static long started;
    static boolean tick(Minecraft game) throws Exception {
        if(stage==25)return GraphSearchPickupRegression.tick(game);
        if (game.player == null || game.level == null || --wait > 0) return false;
        if (game.screen instanceof AnimatedQuestScreen a && a.inputBlocked()) return false;
        if (work != null) { if (!work.isDone()) return false; work.join(); work = null; }
        var server = game.getSingleplayerServer(); var playerId = game.player.getUUID();
        switch (stage) {
            case 0 -> {
                if (!(game.screen instanceof QuestGenerateScreen)) return false;
                check(!server.overworld().getChunkSource().getGenerator().getClass().getSimpleName().contains("Flat"), "Normal terrain required");
                check(QuestRepository.all().stream().noneMatch(q -> q.id().startsWith("auto_story")), "Opening chooser generated tasks");
                game.options.guiScale().set(2); game.getWindow().setWindowed(1920,1080); game.resizeDisplay();
                game.getLanguageManager().setSelected("zh_cn"); game.options.languageCode = "zh_cn";
                work = game.reloadResourcePacks();
            }
            case 1 -> {
                capture(game,"generation-chooser-zh-1920");
                button(game.screen,"generate-cancel").onPress(); wait=5;
            }
            case 2 -> {
                check(game.screen == null, "Cancel did not close chooser");
                work = server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    check(QuestProgressData.get(server).generationOffered(playerId), "First-run choice not acknowledged");
                    var saved = QuestProgressData.get(server).save(new net.minecraft.nbt.CompoundTag(), server.registryAccess());
                    check(QuestProgressData.load(saved, server.registryAccess()).generationOffered(playerId), "First-run acknowledgement did not persist");
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "xero_quest start_generate_gui");
                    return true;
                }); wait=5;
            }
            case 3 -> {
                if (!(game.screen instanceof QuestGenerateScreen)) return false;
                ((StyledChoiceButton<String>)button(game.screen,"generate-namespace")).select("story_fixture");
                button(game.screen,"generate-start").onPress(); wait=10;
            }
            case 4 -> {
                if (QuestGenerator.running()) return false;
                var tasks = QuestRepository.all().stream().filter(q -> q.id().startsWith("auto_story")).toList();
                check(tasks.size()>2, "Progress mode did not add linked recipe tasks");
                var advancement = tasks.stream().filter(q -> q.triggers().stream().anyMatch(t -> t.type().equals("advancement") && t.item().equals("story_fixture:root"))).findFirst().orElseThrow();
                check(!advancement.rewards().isEmpty() && advancement.rewards().stream().allMatch(r -> r.type().equals("item")), "Progress reward is not useful next-stage recipe supplies");
                var ids = new HashSet<String>(); tasks.forEach(q -> ids.add(q.id()));
                for (var q : tasks) check(!q.parents().contains(q.id()) && q.parents().stream().allMatch(ids::contains), "Broken story prerequisite");
                sources = new HashMap<>(); for (var q : tasks) sources.put(q.id(),QuestRepository.source(q.id()));
                work = server.submit(() -> QuestGenerator.start(server,"story_fixture",QuestGenerator.RewardMode.RECIPE,1,QuestGenerator.GenerationMode.PROGRESS,true,(m,f)->{ if(f) throw new AssertionError(m.getString()); })); wait=10;
            }
            case 5 -> {
                if (QuestGenerator.running()) return false;
                for (var e : sources.entrySet()) check(e.getValue().equals(QuestRepository.source(e.getKey())), "Rerun replaced existing task");
                game.setScreen(journal = new QuestScreen()); ClientEditMode.set(true);
                templates = new QuestTemplateScreen(journal,""); game.setScreen(templates); wait=4;
            }
            case 6 -> { button(templates,"template-add-group").onPress(); wait=4; }
            case 7 -> {
                ((EditBox)field(game.screen,"name")).setValue("自定义模板组"); button(game.screen,"template-group-save").onPress(); wait=5;
            }
            case 8 -> {
                if (game.screen != templates) return false;
                check(ClientQuestDefinitions.templateLibrary().groups().contains("自定义模板组"),"Empty template group missing");
                check(QuestCatalog.decode(QuestCatalog.serverJson()).templateLibrary().groups().contains("自定义模板组"),"Template group absent from network catalog");
                check(QuestRepository.groups().stream().noneMatch(g -> g.name().equals("自定义模板组")), "Template group polluted task groups");
                ((EditBox)field(templates,"search")).setValue("主线"); wait=3;
            }
            case 9 -> {
                var source=button(templates,"template-main_acquire");
                var target=button(templates,"template-main_submit");
                templates.mouseClicked(source.getX()+50,source.getY()+12,0);
                set(templates,"dragTemplateStartedAt",System.nanoTime()-400_000_000L);
                templates.mouseDragged(target.getX()+50,target.getBottom()-2,0,0,40);
                templates.mouseReleased(target.getX()+50,target.getBottom()-2,0); wait=6;
            }
            case 10 -> {
                if (field(templates,"pendingOrder") != null) return false;
                var order=ClientQuestDefinitions.templateLibrary().order();
                check(order.indexOf("main_acquire")>order.indexOf("main_submit"),"Long-press template reorder failed");
                capture(game,"template-acquire-order-zh-1920");
                work=server.submit(() -> {
                    try {
                        var player=server.getPlayerList().getPlayer(playerId);
                        for(int i=0;i<3;i++) {
                            String templateId=i==0?"world_acquire":"world_view";
                            var template=QuestTemplateStore.all().stream().filter(t->t.id().equals(templateId)).findFirst().orElseThrow();
                            check(QuestAuthoring.create(player,new QuestAuthoring.Request(QuestAuthoring.Kind.TASK,"generation_move_"+i,"排序任务 "+i,"","","","world","minecraft:oak_log",16,"minecraft:oak_log","","",1,"manual",templateId,null,null,template.defaultConditions(),List.of())).successful(),"Create acquisition fixture");
                        }
                        player.getInventory().clearContent(); player.getInventory().add(new ItemStack(Items.OAK_LOG,16));
                        QuestRuntime.evaluate(player);
                        check(QuestRuntime.completed(player,QuestRepository.find("generation_move_0")),"Holding 16 logs did not auto-complete");
                        check(player.getInventory().countItem(Items.OAK_LOG)==16,"Acquisition template consumed logs");
                        return true;
                    } catch(Exception e){throw new RuntimeException(e);}
                }); wait=5;
            }
            case 11 -> {
                ClientPreferences.get().reducedMotion=false;
                journal=QuestScreen.task(null,"generation_move_1"); set(journal,"editMode",true); game.setScreen(journal); wait=5;
            }
            case 12 -> {
                invoke(journal,"startTaskMove",new Class<?>[]{String.class},"generation_move_1");
                click(journal,button(journal,"order-next")); wait=8;
            }
            case 13 -> {
                if(field(journal,"orderPending")!=null)return false;
                stableButton=button(journal,"order-previous"); wait=12;
            }
            case 14 -> {
                check(stableButton==button(journal,"order-previous"),"Movement rebuild loop still present");
                check(!journal.inputBlocked(),"Movement left input blocked");
                click(journal,button(journal,"order-previous")); wait=8;
            }
            case 15 -> {
                if(field(journal,"orderPending")!=null)return false;
                click(journal,button(journal,"order-done")); check(field(journal,"movingTask").equals(""),"Move mode did not exit");
                game.setScreen(null);
                work=server.submit(()->QuestGenerator.start(server,"minecraft",QuestGenerator.RewardMode.RECIPE,1,QuestGenerator.GenerationMode.STRUCTURE,false,(m,f)->{if(f)throw new AssertionError(m.getString());})); wait=15;
            }
            case 16 -> {
                if(QuestGenerator.running())return false;
                var q=QuestRepository.all().stream().filter(t->t.id().startsWith("auto_story_visit") && t.triggers().stream().anyMatch(c->c.item().endsWith("minecraft:village_plains"))).findFirst().orElseThrow();
                village=q.id();
                check(q.presentation().controls().stream().anyMatch(c->c.graph().nodes().stream().anyMatch(n->n.type().equals("locate_structure"))),"Exploration missing locate action");
                work=server.submit(()->{var p=server.getPlayerList().getPlayer(playerId); QuestRuntime.trigger(p,village,"unlock",true); return QuestRuntime.toggleTracking(p,village);});
                started=System.nanoTime(); wait=10;
            }
            case 17 -> {
                if(QuestHudRenderer.locatedLocation(village)==null){check(System.nanoTime()-started<90_000_000_000L,"Structure tracking never received locate coordinates");return false;}
                var location=QuestHudRenderer.locatedLocation(village); check(location.resolved(),"Located target unresolved");
                QuestUnlockToast.clear(); MailArrivalToast.clear(); game.getToasts().clear(); wait=5;
            }
            case 18 -> {
                var markers=(List<?>)field(QuestHudRenderer.class,"markerSnapshot");
                check(!markers.isEmpty(),"No tracked coordinate marker drawn");
                capture(game,"generated-structure-guidance-zh-1920");
                work=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(playerId); var q=QuestRepository.find(village);
                    String cached=QuestControlData.get(p).guidance(p,q,q.triggers().getFirst().item());
                    check(!cached.isBlank(),"Locate did not persist shared target");
                    check(QuestStructureGuidance.request(p,village).equals("guidance_ready"),"Cached locate was searched again");
                    var position=QuestHudRenderer.locatedLocation(village);
                    p.serverLevel().getChunk(net.minecraft.core.BlockPos.containing(position.x(),position.y(),position.z()));
                    p.teleportTo(p.serverLevel(),position.x(),position.y(),position.z(),p.getYRot(),p.getXRot());
                    QuestRuntime.evaluate(p);
                    check(QuestRuntime.completed(p,q),"Arriving at located village did not complete structure task");
                    return true;
                }); wait=3;
            }
            case 19 -> {
                ClientPreferences.get().reducedMotion=true;
                hud=new HudNotificationLayoutScreen(null); game.setScreen(hud); wait=4;
                ((EditBox)field(hud,"scaleInput")).setValue("10");
                ((EditBox)field(hud,"textScaleInput")).setValue("200");
            }
            case 20 -> {
                var geometry=NoticeGeometry.task(hud.width,10,200,ClientPreferences.get().taskToastIconScalePercent);
                check(geometry.hintY()+game.font.lineHeight*geometry.textScale()<geometry.height(),"Task hint overflows at minimum scale/maximum font");
                int[] bounds=(int[])invoke(hud,"selectedRect",new Class<?>[]{});
                check(Math.abs(bounds[3]-geometry.height()*geometry.scale()*(double)invoke(hud,"sceneScale",new Class<?>[]{}))<2,"Editor task outline differs from runtime dimensions");
                capture(game,"notice-layout-large-font-zh-1920");
                button(hud,"notification-layout-pickup").onPress();
                ((EditBox)field(hud,"scaleInput")).setValue("50");
                ((EditBox)field(hud,"textScaleInput")).setValue("200");wait=4;
            }
            case 21 -> {
                var geometry=NoticeGeometry.pickup(hud.width,50,200,game.font);
                check(geometry.rowHeight()>game.font.lineHeight*geometry.textScale()+10,"Pickup rows overlap at minimum scale/maximum font");
                capture(game,"notice-pickup-small-scale-zh-1920");
                int[] map=(int[])invoke(hud,"minimapAvoidRect",new Class<?>[]{});
                double x=map[0]+map[2]/2.,y=map[1]+map[3]/2.;
                check(hud.mouseClicked(x,y,0),"Map drag not handled");hud.mouseDragged(x-100,y+45,0,-100,45);hud.mouseReleased(x-100,y+45,0);
                check((int)field(hud,"minimapX")>=0 && (int)field(hud,"minimapY")>0,"Minimap region position cannot move");
                game.getWindow().setWindowed(640,480);game.getWindow().setWidth(640);game.getWindow().setHeight(480);game.resizeDisplay();
                button(hud,"notification-layout-task").onPress();
                hud.mouseScrolled(hud.width-30,hud.height/2.,0,-15);wait=10;
            }
            case 22 -> {
                check(button(hud,"notification-play-preview").visible,"Compact properties cannot scroll to preview control");
                button(hud,"notification-play-preview").onPress();check((boolean)field(hud,"previewPlaying"),"Animation preview did not start");
                button(hud,"notification-layout-save").onPress();wait=2;
            }
            case 23 -> {
                check(ClientPreferences.get().minimapAvoidXPercent>=0 && ClientPreferences.get().minimapAvoidYPercent>0,"Custom avoid position not saved");
                game.getWindow().setWindowed(1920,1080);game.getWindow().setWidth(1920);game.getWindow().setHeight(1080);game.resizeDisplay();
                ClientPreferences.get().taskToastScalePercent=46;ClientPreferences.get().taskToastTextScalePercent=100;
                game.setScreen(hud=new HudNotificationLayoutScreen(null));wait=3;
            }
            case 24 -> {
                capture(game,"notification-layout-editor-zh-1920");
                Files.createDirectories(OUT); Files.writeString(OUT.resolve("generation-template-result.txt"),"PASS: first-world offer and cancel; persisted acknowledgement; reopen command; advancement recipe chain and useful rewards; idempotent generation; empty template group network catalog; long-press template reorder; auto-complete 16 logs without consumption; repeated move with animations and stable toolbar; normal-world structure locate, arrival completion, network marker and shared cached coordinates; HUD minimum-scale/maximum-font row bounds; matching runtime/editor dimensions; movable minimap reservation; compact property scrolling, one-shot preview and saved position.\n");
                wait=2;
            }
        }
        stage++;return false;
    }
    private static StyledButton button(Screen s,String id){return s.children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast).filter(b->b.styleId().equals(id)).findFirst().orElseThrow(()->new AssertionError("Missing "+id));}
    private static void click(Screen s,StyledButton b){check(b.active,"Inactive "+b.styleId());s.mouseClicked(b.getX()+b.getWidth()/2.,b.getY()+b.getHeight()/2.,0);s.mouseReleased(b.getX()+b.getWidth()/2.,b.getY()+b.getHeight()/2.,0);}
    private static java.lang.reflect.Field reflected(Object o,String n)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
    private static Object field(Object o,String n)throws Exception{return o instanceof Class<?> c ? staticField(c,n) : reflected(o,n).get(o);}
    private static Object staticField(Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f.get(null);}
    private static void set(Object o,String n,Object v)throws Exception{reflected(o,n).set(o,v);}
    private static Object invoke(Object o,String n,Class<?>[] types,Object...args)throws Exception{var m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}
    private static void capture(Minecraft g,String name)throws Exception{Files.createDirectories(OUT);try(var i=Screenshot.takeScreenshot(g.getMainRenderTarget())){i.writeToFile(OUT.resolve(name+".png"));}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message+" at generation stage "+stage);}
}
