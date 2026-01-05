<div align="right">

[English](README.md)

</div>

<p align="center"><img src="assets/CounterStrikeGrenadesBanner.png" width="500" height="250"></p>

<div align="center">

| ![高爆手雷](assets/he.png) | ![闪光弹](assets/flashbang.png) | ![诱饵弹](assets/decoy.png) | ![燃烧弹](assets/incendiary.png) | ![烟雾弹](assets/smokegrenade.png) | ![燃烧瓶](assets/molotov.png)
|:---:|:---:|:---:|:---:|:---:|:---:|

# Counter Strike Grenade (反恐精英：手榴弹)

本模组将CS2中的战术投掷物带入Minecraft  
*可选与[TACZ（永恒枪械工坊：零）](https://github.com/MCModderAnchor/TACZ)模组进行联动*

</div>

## ✨ 特性
- [x] HE 高爆手雷
- [x] 闪光弹
- [x] 燃烧弹 (与燃烧瓶)
    - [x] CT与T双方拥有不同的燃烧物
    - [x] 与烟雾弹的交互
- [x] 烟雾弹
- [x] 诱饵弹
    - [x] 与Tacz模组联动，可发出逼真的枪声
- [x] 3D模型

## 🛠️ 合成配方
<p align="center"><img src="assets/recipes-3d.png" width="900" height="650"></p>

## 🔗 下载链接
最新版本：

1. **GitHub Releases** : [https://github.com/ThePiSquad/CounterStrikeGrenades/releases](https://github.com/ThePiSquad/CounterStrikeGrenades/releases)
2. **Modrinth** : [https://modrinth.com/mod/counterstrikegrenade](https://modrinth.com/mod/counterstrikegrenade)
3. **CurseForge** : [https://www.curseforge.com/minecraft/mc-mods/counter-strike-grenades](https://www.curseforge.com/minecraft/mc-mods/counter-strike-grenades)

## 💡 高级用法
### 自定义诱饵弹声音
在给予玩家诱饵弹时，你可以通过NBT标签来让它播放一个特定的声音。这对于地图制作和自定义场景非常有用。

使用 `/give` 命令，并添加一个 `DecoySound` 标签，其值为声音的资源路径。

**示例：**
给予自己一个会播放苦力怕爆炸前嘶嘶声的诱饵弹：
```
/give @p csgrenades:decoy{DecoySound:"minecraft:entity.creeper.primed"} 1
```
如果没有提供 `DecoySound` 标签，诱饵弹将默认播放生物的声音。

## 🔫 Tacz 模组联动 (可选)
本模组提供了与 [Tacz 枪械模组](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero) 的可选联动，以增强诱饵弹的功能。

### 动态枪声音效
如果安装了Tacz模组，诱饵弹将优先播放来自Tacz模组的真实枪声，而不是默认的生物声音。

**工作原理：**
1.  当诱饵弹落地并激活时，它会**一次性**扫描投掷玩家的物品栏。
2.  它会识别物品栏中的第一把Tacz枪械 (例如, AK-47, M4A1)。
3.  在它的整个持续时间内，诱饵弹将重复播放那把特定Tacz枪械的“开火”音效。

**优先级：**
Tacz联动具有最高的诱饵弹声音优先级。如果安装了Tacz，它将覆盖任何通过NBT标签设置的自定义声音。如果未安装Tacz，诱饵弹将依次尝试使用NBT自定义声音，或默认的原版声音。

### 诱饵弹爆炸
诱饵弹最后的爆炸已被调整为一个强度非常低 (0.1f) 的原版爆炸。这移除了不真实的击退效果，同时仍然为其生命周期结束提供视觉和声音提示。

## 👨‍💻 面向开发者的API
本模组为其他开发者提供了用于集成的API。

### 检查玩家是否被闪光
你可以使用 `CSGrenadesAPI` 来检查一个玩家当前是否正处于闪光弹效果之下。

**用法 (Java):**
```java
import club.pisquad.minecraft.csgrenades.api.CSGrenadesAPI;
import net.minecraft.world.entity.player.Player;

// 假设你有一个玩家对象，例如 'targetPlayer'
boolean isFlashed = CSGrenadesAPI.isPlayerFlashed(targetPlayer);

if (isFlashed) {
    // 玩家当前被闪
} else {
    // 玩家未被闪
}
```

### 取消投掷物投掷
你可以监听 `GrenadeThrowEvent` 事件并取消它，以阻止手榴弹被投掷。此事件在Forge事件总线上触发。

**用法 (Java):**
```java
import club.pisquad.minecraft.csgrenades.event.GrenadeThrowEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MyEventHandler {
    @SubscribeEvent
    public void onGrenadeThrow(GrenadeThrowEvent event) {
        // 示例：阻止玩家在潜行时扔手榴弹
        if (event.getPlayer().isShiftKeyDown()) {
            event.setCanceled(true); // 这将阻止手榴弹被扔出
        }
    }
}

// 别忘了在Forge事件总线上注册你的事件处理器类：
// MinecraftForge.EVENT_BUS.register(new MyEventHandler());
```

## ⚙️ 配置
在 `1.2.*` 或更高版本中，你可以通过 [Forge的服务端配置系统](https://docs.minecraftforge.net/en/1.20.1/misc/config/#registering-a-configuration)来自定义此模组的行为。

本模组的配置文件名为 `csgrenade-server.toml`，位于存档的 `saves/<存档名>/serverconfig` 文件夹下。

以下是默认配置：
```toml
#Counter Strike Grenade (反恐精英：手榴弹) 的配置
#配置根据手榴弹的类型被分到不同的作用域下
#手榴弹实体是否应穿过屏障方块？
ignore_barrier_block = true
#投掷冷却时间，单位为毫秒
#范围: 0 ~ 60000
grenade_throw_cooldown = 1000
#使用主键（默认为左键）时的投掷速度
#范围: 0.0 ~ 10.0
throw_speed_strong = 1.3
#使用副键（默认为右键）时的投掷速度
#范围: 0.0 ~ 10.0
throw_speed_weak = 0.4
#同时按下主副键时的投掷速度
#范围: 0.0 ~ 10.0
throw_speed_moderate = 1.0
#范围: 0.0 ~ 10.0
player_speed_factor_strong = 1.3
#范围: 0.0 ~ 10.0
player_speed_factor_weak = 0.5
#投掷类型切换的过渡时间，单位为毫秒
#范围: 0 ~ 60000
throw_type_transient_time = 1000
#视野效果（FOV）强度
#范围: 0.0 ~ 1.0
fov_effect_amount = 0.12
#是否对玩家以外的生物造成伤害
damage_non_player_entity = true

[SmokeGrenade]
	#烟雾半径，单位为方块
	#范围: 2 ~ 10
	smoke_radius = 6
	#落地后的引信时间，单位为毫秒
	#范围: 0 ~ 10000
	fuse_time_after_landing = 500
	#烟雾持续时间，单位为毫秒
	#范围: 0 ~ 60000
	smoke_lifetime = 20000
	#烟雾被穿过后开始再生的时间，单位为毫秒
	#范围: 0 ~ 10000
	time_before_regenerate = 1000
	#烟雾再生过程所需时间，单位为毫秒
	#范围: 0 ~ 10000
	regeneration_time = 3000
	#烟雾最大下沉高度
	#范围: 0 ~ 100
	smoke_max_falling_height = 8
	#箭矢穿过烟雾时驱散的半径，单位为方块
	#范围: 0.1 ~ 10.0
	arrow_clear_range = 1.2
	#子弹(例如来自Tacz)穿过烟雾时驱散的半径，单位为方块
	#范围: 0.1 ~ 10.0
	bullet_clear_range = 1.0

[HEGrenade]
	#高爆手雷的伤害遵循线性衰减函数
	#基础伤害
	#范围: 0.0 ~ 100.0
	base_damage = 30.0
	#伤害范围
	#范围: 0.0 ~ 100.0
	damage_range = 5.0
	#爆头伤害加成
	#范围: 0.0 ~ 100.0
	head_damage_boost = 1.5
	#对所有者造成伤害的策略
	#可选值: NEVER, NOT_IN_TEAM, ALWAYS
	causeDamageToOwner = "ALWAYS"
	#爆炸前的引信时间，单位为毫秒
	#范围: 0 ~ 10000
	fuseTime = 2000

[FireGrenade]
	#火焰范围
	#范围: 0 ~ 100
	fire_range = 6
	#火焰持续时间，单位为毫秒
	#范围: 0 ~ 100000
	lifetime = 7000
	#空中爆炸的引信时间，单位为毫秒
	#范围: 0 ~ 100000
	fuse_time = 2000
	#火焰被扑灭的范围
	#范围: 0 ~ 100
	fire_extinguish_range = 6
	#火焰最大向下蔓延的高度
	#范围: 0 ~ 100
	fire_max_spread_downward = 10
	#伤害值
	#范围: 0.0 ~ 100.0
	damage = 3.0
	#火焰伤害达到最大值所需的时间(线性增长)，单位为毫秒
	#范围: 0 ~ 100000
	damage_increase_time = 2000
	#对所有者造成伤害的策略
	#可选值: NEVER, NOT_IN_TEAM, ALWAYS
	causeDamageToOwner = "ALWAYS"

[Flashbang]
	#闪光弹产生显著效果的最大距离
	#范围: 1.0 ~ 256.0
	effectiveRange = 64.0
	#从投掷到引爆的引信时间，单位为毫秒
	#范围: 0 ~ 10000
	fuseTime = 1600
	#最大致盲持续时间（近距离、直视时），单位为秒
	#范围: 0.0 ~ 30.0
	maxDuration = 5.0
	#最小致盲持续时间（完全背对时），单位为秒
	#范围: 0.0 ~ 10.0
	minDuration = 0.25
	#控制效果随距离衰减的曲线。1.0为线性，>1.0则远距离衰减更快（近距离更强）
	#范围: 0.5 ~ 5.0
	distanceDecayExponent = 2.0
```

## ⌨️ 命令
本模组提供了服务端命令，可在游戏内配置手榴弹的行为。你必须拥有操作员权限（等级2）才能使用它们。

### 设置对自己造成伤害的策略
你可以控制高爆手雷和燃烧弹（燃烧弹/燃烧瓶）是否能伤害其所有者。

**用法:**
`/csgrenades <grenadeType> causeDamageToOwner <value>`

-   `<grenadeType>`: 要配置的手榴弹类型。
    -   `hegrenade`
    -   `firegrenade`
-   `<value>`: 对自己造成伤害的策略。
    -   `always`: 手榴弹总是会伤害其所有者。(默认)
    -   `not_in_team`: 仅当启用基于团队的友伤时，手榴弹才会伤害其所有者。
    -   `never`: 手榴弹永远不会伤害其所有者。

**示例:**
`/csgrenades hegrenade causeDamageToOwner never`

### 设置全局配置
你可以配置影响所有手榴弹的全局设置。

**用法:**
`/csgrenades global <setting> <value>`

-   `<setting>`: 要更改的全局设置。
    -   `ignoreBarrierBlock`: 控制手榴弹是否穿过屏障方块。
-   `<value>`: 该设置的值。
    -   `true`: 手榴弹将穿过屏障方块。
    -   `false`: 手榴弹将与屏障方块碰撞。(默认)

**示例:**
`/csgrenades global ignoreBarrierBlock true`

## 🌍 本地化
本模组当前支持以下语言：
-   English (en_us) - 英语
-   简体中文 (zh_cn) - 简体中文

## 🙏 致谢
- [MinecraftForge/MinecraftForge: 对Minecraft基础文件的修改，以协助模组间的兼容性](https://github.com/MinecraftForge/MinecraftForge)
- [thedarkcolour/KotlinForForge: 让Kotlin与Forge更友好。](https://github.com/thedarkcolour/KotlinForForge)
- CI/CD
  - [cloudnode-pro/modrinth-publish: 一个用于将插件版本发布到Modrinth的GitHub Action](https://github.com/cloudnode-pro/modrinth-publish)

## 🏆贡献者
- 程序: [@hukening81](https://github.com/hukening81), [@Dragonzhi](https://github.com/Dragonzhi)
- 美术: [@Dragonzhi](https://github.com/Dragonzhi)
