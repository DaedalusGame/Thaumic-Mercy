package thaumic_mercy;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.common.tiles.crafting.TileResearchTable;

import java.util.*;
import java.util.stream.Stream;

@Mod(modid = ThaumicMercy.MODID, acceptedMinecraftVersions = "[1.12, 1.13)")
@Mod.EventBusSubscriber
public class ThaumicMercy
{
    public static final String MODID = "thaumic_mercy";
    public static final String KEY = "BASICS";
    public static boolean isResearchPatched = false;
    public static Set<TileResearchTable> researchTables = Collections.newSetFromMap(new WeakHashMap());

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLoad(AttachCapabilitiesEvent<TileEntity> event) {
        TileEntity tile = event.getObject();
        if(tile instanceof TileResearchTable) {
            researchTables.add((TileResearchTable) tile);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if(event.player instanceof EntityPlayerMP)
            handlePlayer((EntityPlayerMP) event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        for (TileResearchTable table : researchTables)
            handleResearchTable(table);
        if(!isResearchPatched) {
            patchResearch();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if(!isResearchPatched) {
            patchResearch();
        }
    }

    private static void patchResearch() {
        isResearchPatched = true;

        //And I call that mercy
        for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
            for (ResearchEntry entry : category.research.values()) {
                for (ResearchStage stage : entry.getStages()) {
                    patchResearchEntry(stage);
                }
            }
        }
    }

    private static Object patchObtain(Object obtain) {
        if (obtain instanceof ItemStack) {
            ItemStack stack = (ItemStack) obtain;
            if(!stack.getHasSubtypes() && stack.getMetadata() > 0)
                stack.setItemDamage(0);
            if(stack.isEmpty())
                return null;
            stack.setCount(1);
        }
        return obtain;
    }

    private static void patchResearchEntry(ResearchStage stage) {
        int maxLen = 8;
        ResearchCategory basicCategory = ResearchCategories.getResearchCategory(KEY);
        //nullEmpty(stage);
        if(stage.getObtain() != null) {
            Stream<Object> stream = Arrays.stream(stage.getObtain()).limit(maxLen).map(ThaumicMercy::patchObtain).filter(Objects::nonNull);
            Object[] obtain = stream.toArray();
            if(obtain.length == 0)
                obtain = null;
            stage.setObtain(obtain);
        }
        if(stage.getCraft() != null && stage.getCraft().length == 0)
            stage.setCraft(null);
        if(stage.getCraftReference() != null && stage.getCraftReference().length == 0)
            stage.setCraftReference(null);
        if(stage.getKnow() != null) {
            ResearchStage.Knowledge[] knowSet = stage.getKnow();
            ResearchStage.Knowledge basicObservation = new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, basicCategory, 0);
            ResearchStage.Knowledge basicTheory = new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY, basicCategory, 0);
            for (int i = 0; i < knowSet.length; i++) {
                ResearchStage.Knowledge knowledge = knowSet[i];
                switch (knowledge.type) {
                    case THEORY:
                        basicTheory.amount += knowledge.amount;
                        break;
                    case OBSERVATION:
                        basicObservation.amount += knowledge.amount;
                        break;
                }
            }
            ArrayList<ResearchStage.Knowledge> replacement = new ArrayList<>();
            if (basicObservation.amount > 0)
                replacement.add(basicObservation);
            if (basicTheory.amount > 0)
                replacement.add(basicTheory);
            if(!replacement.isEmpty())
                stage.setKnow(replacement.toArray(new ResearchStage.Knowledge[0]));
            else
                stage.setKnow(null);
        }
    }

    private static void handlePlayer(EntityPlayerMP player) {
        IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);

        int totalTheory = 0;
        int totalObservation = 0;
        ResearchCategory basicCategory = ResearchCategories.getResearchCategory(KEY);
        for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
            if(category != basicCategory) {
                int theory = knowledge.getKnowledgeRaw(IPlayerKnowledge.EnumKnowledgeType.THEORY, category);
                int observation = knowledge.getKnowledgeRaw(IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, category);

                totalTheory += theory;
                totalObservation += observation;

                knowledge.addKnowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY,category,-theory);
                knowledge.addKnowledge(IPlayerKnowledge.EnumKnowledgeType.OBSERVATION,category,-observation);
            }
        }
        knowledge.addKnowledge(IPlayerKnowledge.EnumKnowledgeType.THEORY,basicCategory,totalTheory);
        knowledge.addKnowledge(IPlayerKnowledge.EnumKnowledgeType.OBSERVATION,basicCategory,totalObservation);
    }

    private static void handleResearchTable(TileResearchTable table) {
        if(table.data == null)
            return;
        ResearchTableData data = table.data;
        if(!data.isComplete())
            return;
        if(data.categoryTotals.size() <= 1 && data.categoryTotals.containsKey(KEY))
            return;
        int total = data.categoryTotals.entrySet().stream().filter(entry -> !data.categoriesBlocked.contains(entry.getKey())).mapToInt(Map.Entry::getValue).sum();
        data.categoryTotals.clear();
        data.categoryTotals.put(KEY,total);
    }
}
