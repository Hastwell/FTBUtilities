package latmod.ftbu.world;

import com.mojang.authlib.GameProfile;
import cpw.mods.fml.relauncher.*;
import ftb.lib.*;
import ftb.lib.item.StringIDInvLoader;
import ftb.lib.notification.*;
import latmod.ftbu.api.EventLMPlayerServer;
import latmod.ftbu.mod.FTBU;
import latmod.ftbu.mod.client.FTBUClickAction;
import latmod.ftbu.net.MessageLMPlayerUpdate;
import latmod.ftbu.world.claims.*;
import latmod.ftbu.world.ranks.*;
import latmod.lib.*;
import latmod.lib.config.ConfigGroup;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.World;

public class LMPlayerServer extends LMPlayer // LMPlayerClient
{
	public static int lastPlayerID = 0;
	
	public static final int nextPlayerID()
	{ return ++lastPlayerID; }
	
	private final PersonalSettings settings;
	private NBTTagCompound serverData = null;
	public EntityPos lastPos, lastDeath;
	public final LMPlayerStats stats;
	private EntityPlayerMP entityPlayer = null;
	public int lastChunkType = -99;
	public final Warps homes;
	
	public static LMPlayerServer get(Object o) throws CommandException
	{
		LMPlayerServer p = LMWorldServer.inst.getPlayer(o);
		if(p == null) throw new PlayerNotFoundException();
		return p;
	}
	
	public LMPlayerServer(LMWorldServer w, int i, GameProfile gp)
	{
		super(w, i, gp);
		settings = new PersonalSettings(this);
		stats = new LMPlayerStats(this);
		homes = new Warps();
	}
	
	public Side getSide()
	{ return Side.SERVER; }
	
	public boolean isOnline()
	{ return entityPlayer != null; }
	
	public LMPlayerServer toPlayerMP()
	{ return this; }
	
	@SideOnly(Side.CLIENT)
	public LMPlayerClient toPlayerSP()
	{ return null; }
	
	public EntityPlayerMP getPlayer()
	{ return entityPlayer; }
	
	public void setPlayer(EntityPlayerMP ep)
	{ entityPlayer = ep; }
	
	public PersonalSettings getSettings()
	{ return settings; }
	
	public void sendUpdate()
	{
		new EventLMPlayerServer.UpdateSent(this).post();
		if(isOnline()) new MessageLMPlayerUpdate(this, true).sendTo(getPlayer());
		for(EntityPlayerMP ep : FTBLib.getAllOnlinePlayers(getPlayer()))
			new MessageLMPlayerUpdate(this, false).sendTo(ep);
	}
	
	public boolean isOP()
	{ return FTBLib.isOP(gameProfile); }
	
	public EntityPos getPos()
	{
		EntityPlayerMP ep = getPlayer();
		if(ep != null)
		{
			if(lastPos == null) lastPos = new EntityPos(ep);
			else lastPos.set(ep);
		}
		
		return lastPos;
	}
	
	// Reading / Writing //
	
	public void getInfo(FastList<IChatComponent> info)
	{
		refreshStats();
		long ms = LMUtils.millis();
		new EventLMPlayerServer.CustomInfo(this, info).post();
		stats.getInfo(info, ms);
	}
	
	public void refreshStats()
	{
		if(isOnline())
		{
			stats.refreshStats();
			if(!world.settings.isOutsideBorderD(entityPlayer.dimension, entityPlayer.posX, entityPlayer.posZ))
				getPos();
		}
	}
	
	public void readFromServer(NBTTagCompound tag)
	{
		friends.clear();
		friends.addAll(tag.getIntArray("Friends"));
		
		commonPublicData = tag.getCompoundTag("CustomData");
		commonPrivateData = tag.getCompoundTag("CustomPrivateData");
		
		StringIDInvLoader.readItemsFromNBT(lastArmor, tag, "LastItems");
		
		stats.readFromNBT(tag.getCompoundTag("Stats"));
		
		serverData = tag.hasKey("ServerData") ? tag.getCompoundTag("ServerData") : null;
		
		if(tag.hasKey("LastPos"))
		{
			if(lastPos == null) lastPos = new EntityPos();
			lastPos.readFromNBT(tag.getCompoundTag("LastPos"));
		}
		else lastPos = null;
		
		if(tag.hasKey("LastDeath"))
		{
			if(lastDeath == null) lastDeath = new EntityPos();
			lastDeath.readFromNBT(tag.getCompoundTag("LastDeath"));
		}
		else lastDeath = null;
		

		NBTTagCompound settingsTag = tag.getCompoundTag("Settings");
		settings.readFromServer(settingsTag);
		renderBadge = settingsTag.hasKey("Badge") ? settingsTag.getBoolean("Badge") : true;
		
		homes.readFromNBT(tag, "Homes");
	}
	
	public void writeToServer(NBTTagCompound tag)
	{
		refreshStats();
		
		if(!friends.isEmpty())
			tag.setIntArray("Friends", friends.toArray());
		
		if(commonPublicData != null && !commonPublicData.hasNoTags()) tag.setTag("CustomData", commonPublicData);
		if(commonPrivateData != null && !commonPrivateData.hasNoTags()) tag.setTag("CustomPrivateData", commonPrivateData);
		
		StringIDInvLoader.writeItemsToNBT(lastArmor, tag, "LastItems");
		
		if(serverData != null && !serverData.hasNoTags()) tag.setTag("ServerData", serverData);
		
		if(lastPos != null)
		{
			NBTTagCompound tag1 = new NBTTagCompound();
			lastPos.writeToNBT(tag1);
			tag.setTag("LastPos", tag1);
		}
		
		if(lastDeath != null)
		{
			NBTTagCompound tag1 = new NBTTagCompound();
			lastDeath.writeToNBT(tag1);
			tag.setTag("LastDeath", tag1);
		}
		
		NBTTagCompound statsTag = new NBTTagCompound();
		stats.writeToNBT(statsTag);
		tag.setTag("Stats", statsTag);
		
		NBTTagCompound settingsTag = new NBTTagCompound();
		settings.writeToServer(settingsTag);
		settingsTag.setBoolean("Badge", renderBadge);
		tag.setTag("Settings", settingsTag);
		
		homes.writeToNBT(tag, "Homes");
	}
	
	public void writeToNet(ByteIOStream io, boolean self)
	{
		refreshStats();
		new EventLMPlayerServer.DataSaved(this).post();
		
		io.writeBoolean(isOnline());
		io.writeIntArray(friends.toArray(), ByteCount.SHORT);
		
		IntList otherFriends = new IntList();
		
		for(int i = 0; i < LMWorldServer.inst.players.size(); i++)
		{
			LMPlayer p = LMWorldServer.inst.players.get(i);
			if(p.friends.contains(playerID)) otherFriends.add(p.playerID);
		}
		
		io.writeIntArray(otherFriends.toArray(), ByteCount.SHORT);
		LMNBTUtils.writeTag(io, commonPublicData);
		
		if(self)
		{
			settings.writeToNet(io);
			
			LMNBTUtils.writeTag(io, commonPrivateData);
			io.writeInt(getClaimedChunks());
			io.writeInt(getRank().config.max_claims.get());
			
			ConfigGroup group = new ConfigGroup("rank");
			group.addAll(RankConfig.class, getRank().config);
			group.write(io);
		}
	}
	
	public void onPostLoaded()
	{ new EventLMPlayerServer.DataLoaded(this).post(); }
	
	public void checkNewFriends()
	{
		if(isOnline())
		{
			FastList<String> requests = new FastList<String>();
			
			for(int i = 0; i < LMWorldServer.inst.players.size(); i++)
			{
				LMPlayer p1 = LMWorldServer.inst.players.get(i);
				if(p1.isFriendRaw(this) && !isFriendRaw(p1))
					requests.add(p1.getName());
			}
			
			if(requests.size() > 0)
			{
				IChatComponent cc = new ChatComponentTranslation(FTBU.mod.assets + "label.new_friends");
				cc.getChatStyle().setColor(EnumChatFormatting.GREEN);
				Notification n = new Notification("new_friend_requests", cc, 6000);
				n.setDesc(new ChatComponentTranslation(FTBU.mod.assets + "label.new_friends_click"));
				
				MouseAction mouse = new MouseAction(FTBUClickAction.FRIEND_ADD_ALL, null);
				requests.sort(null);
				mouse.hover = new IChatComponent[requests.size()];
				for(int i = 0; i < mouse.hover.length; i++)
					mouse.hover[i] = new ChatComponentText(requests.get(i));
				n.setMouseAction(mouse);
				
				FTBLib.notifyPlayer(getPlayer(), n);
			}
		}
	}
	
	public Rank getRank()
	{ return Ranks.getRank(this); }
	
	public void claimChunk(int dim, int cx, int cz)
	{
		RankConfig c = getRank().config;
		if(c.dimension_blacklist.get().contains(dim)) return;
		int max = c.max_claims.get();
		if(max == 0) return;
		if(getClaimedChunks() >= max) return;
		
		ChunkType t = LMWorldServer.inst.claimedChunks.getType(dim, cx, cz);
		if(!t.isClaimed() && t.isChunkOwner(this) && LMWorldServer.inst.claimedChunks.put(new ClaimedChunk(playerID, dim, cx, cz)))
			sendUpdate();
	}
	
	public void unclaimChunk(int dim, int cx, int cz)
	{
		if(LMWorldServer.inst.claimedChunks.getType(dim, cx, cz).isChunkOwner(this) && LMWorldServer.inst.claimedChunks.remove(dim, cx, cz))
			sendUpdate();
	}
	
	public void unclaimAllChunks(Integer dim)
	{
		FastList<ClaimedChunk> list = LMWorldServer.inst.claimedChunks.getChunks(this, dim);
		int size0 = list.size();
		if(size0 == 0) return;
		
		for(int i = 0; i < size0; i++)
		{
			ClaimedChunk c = list.get(i);
			LMWorldServer.inst.claimedChunks.remove(c.dim, c.pos.chunkXPos, c.pos.chunkZPos);
		}
		
		sendUpdate();
	}
	
	public int getClaimedChunks()
	{ return LMWorldServer.inst.claimedChunks.getChunks(this, null).size(); }
	
	public NBTTagCompound getServerData()
	{
		if(serverData == null)
			serverData = new NBTTagCompound();
		return serverData;
	}
	
	public void setLoaded(World world, int cx, int cz, boolean flag)
	{
	}
	
	public void loadAllChunks(World w)
	{
		/* FIXME ChunkLoading
		if(w == null || w.isRemote) return;
		unloadAllChunks(w);
		
		ForgeChunkManager.Ticket ticket = FTBUChunkEventHandler.instance.request(w, owner);
		
		if(ticket != null)
		{
			for(ClaimedChunk cc : chunks) if(cc.isChunkloaded && cc.dim == w.provider.dimensionId && !cc.isForced)
			{
				ForgeChunkManager.forceChunk(ticket, cc.pos);
				cc.isForced = true;
			}
		}
		*/
	}
	
	public void unloadAllChunks(World w)
	{
		/*
		if(w == null || w.isRemote) return;
		
		ForgeChunkManager.Ticket ticket = FTBUChunkEventHandler.instance.request(w, owner);
		if(ticket != null)
		{
			for(ClaimedChunk cc : chunks) if(cc.isChunkloaded && cc.dim == w.provider.dimensionId && cc.isForced)
			{
				ForgeChunkManager.unforceChunk(ticket, cc.pos);
				cc.isForced = false;
			}
		}
		*/
	}
}