package chanhne.AntiCheat.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public class HighestSolidBlock {

    public static Location get(World world, int x, int z) {
        // Duyệt từ cao nhất xuống (Y=319 cho 1.21, nhưng dùng maxHeight)
        for (int y = world.getMaxHeight() - 1; y > 0; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            // Chỉ lấy block rắn (không bao gồm nước, lava vì isSolid() trả false)
            // Nếu muốn loại trừ một số block đặc biệt, có thể thêm kiểm tra tên hoặc Material set
            if (type.isSolid()) {
                // Kiểm tra block phía trên là air (hoặc có thể đứng)
                Block above = world.getBlockAt(x, y + 1, z);
                if (above.getType() == Material.AIR) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
        }
        // Fallback: spawn của world
        return world.getSpawnLocation();
    }
}