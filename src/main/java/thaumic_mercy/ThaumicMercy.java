package thaumic_mercy;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Mod(modid = ThaumicMercy.MODID, acceptedMinecraftVersions = "[1.12, 1.13)")
@Mod.EventBusSubscriber
public class ThaumicMercy
{
    public static final String MODID = "thaumic_mercy";
    public static final String KEY = "BASICS";
    public static boolean isResearchPatched = false;
    public static Set<TileResearchTable> researchTables = Collections.newSetFromMap(new WeakHashMap());

    public static Configuration config;

    public static boolean snapCategories = true;
    public static boolean patchMetadata = true;

    public static int maxItems = 8;
    public static int maxStack = 1;
    public static int maxCraft = 4;
    public static int maxResearch = 4;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();

        maxItems = config.getInt("maxItems","research",maxItems,0,Integer.MAX_VALUE,"How many items can be consumed by a single research step.");
        maxStack = config.getInt("maxStack","research",maxStack,1,Integer.MAX_VALUE,"How many items can be consumed PER STACK by a single research step.");
        maxCraft = config.getInt("maxCraft","research",maxCraft,0,Integer.MAX_VALUE,"How many crafts can be required by a single research step.");
        maxResearch = config.getInt("maxResearch","research",maxResearch,0,Integer.MAX_VALUE,"How many discoveries can be required by a single research step.");

        snapCategories = config.getBoolean("snapCategories","research", snapCategories, "Whether categorical theories and observations should be flattened.");
        patchMetadata = config.getBoolean("patchMetadata","research", patchMetadata, "Whether illegal metadata should be automatically patched.");

        if (config.hasChanged())
        {
            config.save();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLoad(AttachCapabilitiesEvent<TileEntity> event) {
        if(snapCategories) {
            TileEntity tile = event.getObject();
            if (tile instanceof TileResearchTable) {
                researchTables.add((TileResearchTable) tile);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if(snapCategories) {
            if (event.player instanceof EntityPlayerMP)
                handlePlayer((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if(snapCategories)
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
            if(patchMetadata && !stack.getHasSubtypes() && stack.getMetadata() > 0)
                stack.setItemDamage(0);
            if(stack.isEmpty())
                return null;
            if(stack.getCount() > maxStack)
            stack.setCount(maxStack);
        }
        return obtain;
    }

    private static void patchResearchEntry(ResearchStage stage) {
        ResearchCategory basicCategory = ResearchCategories.getResearchCategory(KEY);
        //nullEmpty(stage);
        if(stage.getObtain() != null) {
            Stream<Object> stream = Arrays.stream(stage.getObtain()).limit(maxItems).map(ThaumicMercy::patchObtain).filter(Objects::nonNull);
            Object[] obtain = stream.toArray();
            if(obtain.length == 0)
                obtain = null;
            stage.setObtain(obtain);
        }
        if(stage.getCraft() != null) {
            Stream<Object> stream = Arrays.stream(stage.getCraft()).limit(maxCraft).filter(Objects::nonNull);
            Object[] craft = stream.toArray();
            if(craft.length == 0)
                craft = null;
            stage.setCraft(craft);
        }
        int crafts = stage.getCraft() != null ? stage.getCraft().length : 0;
        if(stage.getCraftReference() != null) {
            IntStream stream = Arrays.stream(stage.getCraftReference()).limit(crafts);
            int[] craft = stream.toArray();
            if(craft.length == 0)
                craft = null;
            stage.setCraftReference(craft);
        }
        if(stage.getResearch() != null) {
            Stream<String> stream = Arrays.stream(stage.getResearch()).limit(maxResearch).filter(Objects::nonNull);
            String[] research = stream.toArray(String[]::new);
            if(research.length == 0)
                research = null;
            stage.setResearch(research);
        }

        if(stage.getCraft() == null && stage.getKnow() == null && stage.getObtain() == null && stage.getResearch() == null)
            stage.setKnow(new ResearchStage.Knowledge[]{new ResearchStage.Knowledge(IPlayerKnowledge.EnumKnowledgeType.OBSERVATION,basicCategory,1)});
        if(stage.getKnow() != null && snapCategories) {
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
