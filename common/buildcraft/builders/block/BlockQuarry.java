/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.block;

import java.util.Arrays;
import java.util.List;

import buildcraft.builders.BCBuildersBlocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.builders.tile.TileQuarry;
import buildcraft.lib.block.BlockBCTile_Neptune;
import buildcraft.lib.block.IBlockWithFacing;
import buildcraft.lib.misc.CapUtil;

public class BlockQuarry extends BlockBCTile_Neptune implements IBlockWithFacing {
    public BlockQuarry(Material material, String id) {
        super(material, id);
    }

    @Override
    protected void addProperties(List<IProperty<?>> properties) {
        super.addProperties(properties);
        properties.addAll(BuildCraftProperties.CONNECTED_MAP.values());
    }

    private boolean isConnected(IBlockAccess world, BlockPos pos, IBlockState state, EnumFacing side) {
        EnumFacing facing = side;
        if (Arrays.asList(EnumFacing.HORIZONTALS).contains(facing)) {
            facing = EnumFacing.getHorizontal(side.getHorizontalIndex() + 2 + state.getValue(getFacingProperty()).getHorizontalIndex());
        }
        TileEntity tile = world.getTileEntity(pos.offset(facing));
        return tile != null && tile.hasCapability(CapUtil.CAP_ITEMS, facing.getOpposite());
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        for (EnumFacing face : EnumFacing.VALUES) {
            state = state.withProperty(BuildCraftProperties.CONNECTED_MAP.get(face), isConnected(world, pos, state, face));
        }
        return state;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileQuarry();
    }

    @Override
    public boolean canBeRotated(World world, BlockPos pos, IBlockState state, EnumFacing sideWrenched) {
        return false;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileQuarry) {
            for (BlockPos blockPos : ((TileQuarry) tile).getFramePositions(state)) {
                if (world.getBlockState(blockPos).getBlock() == BCBuildersBlocks.frame) {
                    world.setBlockToAir(blockPos);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }
}
