package latmod.core;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Pattern;

import latmod.core.mod.LC;
import latmod.core.util.FastList;
import net.minecraft.block.*;
import net.minecraft.command.ICommandSender;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.item.crafting.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import net.minecraftforge.oredict.*;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonWriter;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.*;
import cpw.mods.fml.common.registry.*;
import cpw.mods.fml.relauncher.*;

public class LatCoreMC
{
	public static final String MC_VERSION = "1.7.10";
	
	public static boolean enableOreRecipes = true;
	public static final int ANY = OreDictionary.WILDCARD_VALUE;
	public static final int TOP = ForgeDirection.UP.ordinal();
	public static final int BOTTOM = ForgeDirection.DOWN.ordinal();
	
	public static final int NBT_INT = 3;
	public static final int NBT_STRING = 8;
	public static final int NBT_LIST = 9;
	public static final int NBT_MAP = 10;
	public static final int NBT_INT_ARRAY = 11;
	
	public static final boolean isDevEnv = LC.MOD_VERSION.equals("@VERSION@");
	
	public static final Pattern textFormattingPattern = Pattern.compile("(?i)" + String.valueOf('\u00a7') + "[0-9A-FK-OR]");
	
	public static final Configuration loadConfig(FMLPreInitializationEvent e, String s)
	{ return new Configuration(new File(e.getModConfigurationDirectory(), s)); }
	
	public static final CreativeTabs createTab(final String s, final ItemStack icon)
	{
		CreativeTabs tab = new CreativeTabs(s)
		{
			@SideOnly(Side.CLIENT)
			public ItemStack getIconItemStack()
			{ return icon; }
			
			@SideOnly(Side.CLIENT)
			public Item getTabIconItem()
			{ return icon.getItem(); }
		};
		
		return tab;
	}
	
	/** Prints message to chat (doesn't translate it) */
	public static final void printChat(ICommandSender ep, Object o)
	{
		if(ep == null) System.out.println(o);
		else ep.addChatMessage(new ChatComponentText("" + o));
	}
	
	// Registry methods //
	
	public static final void addItem(Item i, String name)
	{ GameRegistry.registerItem(i, name); }
	
	public static final void addBlock(Block b, Class<? extends ItemBlock> c, String name)
	{ GameRegistry.registerBlock(b, c, name); }
	
	public static final void addBlock(Block b, String name)
	{ addBlock(b, ItemBlock.class, name); }
	
	public static final void addTileEntity(Class<? extends TileEntity> c, String s)
	{ GameRegistry.registerTileEntity(c, s); }
	
	public static final void addEntity(Class<? extends Entity> c, String s, int id, Object mod)
	{ EntityRegistry.registerModEntity(c, s, id, mod, 50, 1, true); }
	
	public static final int getNewEntityID()
	{ return EntityRegistry.findGlobalUniqueEntityId(); }

	public static void addSmeltingRecipe(ItemStack out, ItemStack in, float xp)
	{ FurnaceRecipes.smelting().func_151394_a(in, out, xp); }
	
	@SuppressWarnings("all")
	public static IRecipe addRecipe(IRecipe r)
	{ CraftingManager.getInstance().getRecipeList().add(r); return r; }
	
	
	public static IRecipe addRecipe(ItemStack out, Object... in)
	{
		if(!enableOreRecipes) return GameRegistry.addShapedRecipe(out, in);
		else return addRecipe(new ShapedOreRecipe(out, in));
	}
	
	public static IRecipe addShapelessRecipe(ItemStack out, Object... in)
	{
		if(!enableOreRecipes)
		{
			ArrayList<ItemStack> al = new ArrayList<ItemStack>();
			int i = in.length;
			
			for (int j = 0; j < i; ++j)
			{
				Object o = in[j];
				
				if (o instanceof ItemStack)
				al.add(((ItemStack)o).copy());
				
				else if (o instanceof Item)
				al.add(new ItemStack((Item)o));
				
				else
				{
					if (!(o instanceof Block))
					throw new RuntimeException("Invalid shapeless recipy!");
					al.add(new ItemStack((Block)o));
				}
			}
			
			return addRecipe(new ShapelessRecipes(out, al));
		}
		else return addRecipe(new ShapelessOreRecipe(out, in));
	}
	
	public static void addOreDictionary(String name, ItemStack is)
	{
		ItemStack is1 = InvUtils.singleCopy(is);
		if(!getOreDictionary(name).contains(is1))
		OreDictionary.registerOre(name, is1);
	}
	
	public static FastList<ItemStack> getOreDictionary(String name)
	{
		FastList<ItemStack> l = new FastList<ItemStack>();
		l.addAll(OreDictionary.getOres(name));
		return l;
	}
	
	public static void addWorldGenerator(IWorldGenerator i, int w)
	{ GameRegistry.registerWorldGenerator(i, w); }
	
	public static void addGuiHandler(Object mod, IGuiHandler i)
	{ NetworkRegistry.INSTANCE.registerGuiHandler(mod, i); }
	
	public static Fluid addFluid(Fluid f)
	{
		Fluid f1 = FluidRegistry.getFluid(f.getName());
		if(f1 != null) return f1;
		FluidRegistry.registerFluid(f);
		return f;
	}
	
	public static boolean canUpdate()
	{ return getEffectiveSide().isServer(); }
	
	public static Side getEffectiveSide()
	{ return FMLCommonHandler.instance().getEffectiveSide(); }
	
	public static ResourceLocation getLocation(String id, String s)
	{ return new ResourceLocation(id.toLowerCase(), s); }
	
	public static String getPath(ResourceLocation res)
	{ return "/assets/" + res.getResourceDomain() + "/" + res.getResourcePath(); }
	
	public static ForgeDirection get2DRotation(EntityLivingBase el)
	{
		int i = MathHelper.floor_float(el.rotationYaw * 4F / 360F + 0.5F) & 3;
		if(i == 0) return ForgeDirection.NORTH;
		else if(i == 1) return ForgeDirection.EAST;
		else if(i == 2) return ForgeDirection.SOUTH;
		else if(i == 3) return ForgeDirection.WEST;
		return ForgeDirection.UNKNOWN;
	}
	
	public static ForgeDirection get3DRotation(World w, int x, int y, int z, EntityLivingBase el)
	{ return ForgeDirection.values()[BlockPistonBase.determineOrientation(w, x, y, z, el)]; }
	
	public static Item getItemFromRegName(String s)
	{ return (Item)Item.itemRegistry.getObject(s); }
	
	public static ItemStack getStackFromRegName(String s, int dmg)
	{
		Item i = getItemFromRegName(s);
		if(i != null) return new ItemStack(i, dmg);
		return null;
	}

	public static String getRegName(Item item, boolean removeMCDomain)
	{
		String s = Item.itemRegistry.getNameForObject(item);
		if(s != null && removeMCDomain && s.startsWith("minecraft:"))
			s = s.substring(10); return s;
	}
	
	public static EntityPlayer getPlayer(World w, UUID id)
	{ return w.func_152378_a(id); }
	
	//TODO: Still need to fix this
	@Deprecated
	public static void teleportEntity(Entity e, int dim)
	{
		if ((e.worldObj.isRemote) || (e.isDead) || e.dimension == dim) return;
		
		LatCoreMC.printChat(null, "Teleporting to dimension " + dim);
		
		e.worldObj.theProfiler.startSection("changeDimension");
		MinecraftServer minecraftserver = MinecraftServer.getServer();
		int j = e.dimension;
		WorldServer worldserver = minecraftserver.worldServerForDimension(j);
		WorldServer worldserver1 = minecraftserver.worldServerForDimension(dim);
		e.dimension = dim;
		e.worldObj.removeEntity(e);
		e.isDead = false;
		e.worldObj.theProfiler.startSection("reposition");
		minecraftserver.getConfigurationManager().transferEntityToWorld(e, j, worldserver, worldserver1);
		e.worldObj.theProfiler.endStartSection("reloading");
		Entity entity = EntityList.createEntityByName(EntityList.getEntityString(e), worldserver1);
		if (entity != null)
		{
			entity.copyDataFrom(e, true);
			worldserver1.spawnEntityInWorld(entity);
		}
		e.isDead = true;
		e.worldObj.theProfiler.endSection();
		worldserver.resetUpdateEntityTick();
		worldserver1.resetUpdateEntityTick();
		e.worldObj.theProfiler.endSection();
	}
	
	public static <T> T fromJson(String s, Type t)
	{
		if(s == null || s.length() < 2) s = "{}";
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
		return gson.fromJson(s, t);
	}
	
	public static <T> T fromJsonFromFile(File f, Type t)
	{
		try
		{
			FileInputStream fis = new FileInputStream(f);
			byte[] b = new byte[fis.available()];
			fis.read(b); fis.close();
			return fromJson(new String(b), t);
		}
		catch(Exception e)
		{ e.printStackTrace(); return null; }
	}
	
	public static String toJson(Object o, boolean asTree)
	{
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
		
		if(asTree)
		{
			StringWriter sw = new StringWriter();
			JsonWriter jw = new JsonWriter(sw);
			jw.setIndent("\t");
			gson.toJson(o, o.getClass(), jw);
			return sw.toString();
		}
		
		return gson.toJson(o);
	}
	
	public static void toJsonFile(File f, Object o)
	{
		String s = toJson(o, true);
		
		try
		{
			if(!f.exists())
			{
				File f0 = f.getParentFile();
				if(!f0.exists()) f0.mkdirs();
				f.createNewFile();
			}
			
			FileOutputStream fos = new FileOutputStream(f);
			fos.write(s.getBytes()); fos.close();
		}
		catch(Exception e)
		{ e.printStackTrace(); }
	}

	public static MovingObjectPosition rayTrace(EntityPlayer ep, double d)
	{
		//ep.yOffset
		Vec3 pos = Vec3.createVectorHelper(ep.posX, ep.posY + 1.62D, ep.posZ);
		Vec3 look = ep.getLook(1F);
		Vec3 vec = pos.addVector(look.xCoord * d, look.yCoord * d, look.zCoord * d);
		//return ep.worldObj.rayTraceBlocks_do_do(pos, vec, false, true);
		return ep.worldObj.func_147447_a(pos, vec, false, true, false);
	}
	
	public static MovingObjectPosition rayTrace(EntityPlayer ep)
	{ return rayTrace(ep, LC.proxy.getReachDist(ep)); }
	
	public static <K, V> Type getMapType(Type K, Type V)
	{ return new TypeToken<Map<K, V>>() {}.getType(); }
	
	public static <E> Type getListType(Type E)
	{ return new TypeToken<List<E>>() {}.getType(); }
	
	public static String removeFormatting(String s)
	{ return textFormattingPattern.matcher(s).replaceAll(""); }
	
	public static MovingObjectPosition collisionRayTrace(World w, int x, int y, int z, Vec3 start, Vec3 end, FastList<AxisAlignedBB> boxes)
	{
		if(boxes == null || boxes.isEmpty()) return null;
		
		for(int i = 0; i < boxes.size(); i++)
		{
			MovingObjectPosition mop = collisionRayTrace(w, x, y, z, start, end, boxes.get(i));
			if(mop != null) return mop;
		}
		
		return null;
	}
	
	public static MovingObjectPosition collisionRayTrace(World w, int x, int y, int z, Vec3 start, Vec3 end, AxisAlignedBB aabb)
	{
		Vec3 pos = start.addVector(-x, -y, -z);
		Vec3 rot = end.addVector(-x, -y, -z);
		
		Vec3 xmin = pos.getIntermediateWithXValue(rot, aabb.minX);
		Vec3 xmax = pos.getIntermediateWithXValue(rot, aabb.maxX);
		Vec3 ymin = pos.getIntermediateWithYValue(rot, aabb.minY);
		Vec3 ymax = pos.getIntermediateWithYValue(rot, aabb.maxY);
		Vec3 zmin = pos.getIntermediateWithZValue(rot, aabb.minZ);
		Vec3 zmax = pos.getIntermediateWithZValue(rot, aabb.maxZ);
		
		if (!isVecInsideYZBounds(xmin, aabb)) xmin = null;
		if (!isVecInsideYZBounds(xmax, aabb)) xmax = null;
		if (!isVecInsideXZBounds(ymin, aabb)) ymin = null;
		if (!isVecInsideXZBounds(ymax, aabb)) ymax = null;
		if (!isVecInsideXYBounds(zmin, aabb)) zmin = null;
		if (!isVecInsideXYBounds(zmax, aabb)) zmax = null;
		Vec3 v = null;
		
		if (xmin != null && (v == null || pos.squareDistanceTo(xmin) < pos.squareDistanceTo(v))) v = xmin;
		if (xmax != null && (v == null || pos.squareDistanceTo(xmax) < pos.squareDistanceTo(v))) v = xmax;
		if (ymin != null && (v == null || pos.squareDistanceTo(ymin) < pos.squareDistanceTo(v))) v = ymin;
		if (ymax != null && (v == null || pos.squareDistanceTo(ymax) < pos.squareDistanceTo(v))) v = ymax;
		if (zmin != null && (v == null || pos.squareDistanceTo(zmin) < pos.squareDistanceTo(v))) v = zmin;
		if (zmax != null && (v == null || pos.squareDistanceTo(zmax) < pos.squareDistanceTo(v))) v = zmax;
		if (v == null) return null; else
		{
			int side = -1;

			if (v == xmin) side = 4;
			if (v == xmax) side = 5;
			if (v == ymin) side = 0;
			if (v == ymax) side = 1;
			if (v == zmin) side = 2;
			if (v == zmax) side = 3;
			
			return new MovingObjectPosition(x, y, z, side, v.addVector(x, y, z));
		}
	}
	
	private static boolean isVecInsideYZBounds(Vec3 v, AxisAlignedBB aabb)
	{ return v == null ? false : v.yCoord >= aabb.minY && v.yCoord <= aabb.maxY && v.zCoord >= aabb.minZ && v.zCoord <= aabb.maxZ; }
	
	private static boolean isVecInsideXZBounds(Vec3 v, AxisAlignedBB aabb)
	{ return v == null ? false : v.xCoord >= aabb.minX && v.xCoord <= aabb.maxX && v.zCoord >= aabb.minZ && v.zCoord <= aabb.maxZ; }
	
	private static boolean isVecInsideXYBounds(Vec3 v, AxisAlignedBB aabb)
	{ return v == null ? false : v.xCoord >= aabb.minX && v.xCoord <= aabb.maxX && v.yCoord >= aabb.minY && v.yCoord <= aabb.maxY; }
}