# Better Combat Swing Guard

面向 Minecraft 1.19.2 与 Better Combat 1.7.1 的附属模组。支持 Fabric 和 Forge。

## 功能

玩家使用带有 Better Combat 武器属性的武器挥砍时，整段攻击动画都会成为一个自动格挡窗口：

- 只抵挡玩家面前的攻击，方向判断沿用原版盾牌逻辑。
- 原版标记为可穿盾的伤害和带穿透的箭不会被抵挡。
- 成功格挡时沿用原版盾牌的伤害归零、声音、状态反馈和攻击者反作用逻辑。
- 伤害不低于 3 点时，当前挥砍武器按照原版盾牌公式损失耐久：1 + floor(原始伤害)。
- 斧类攻击触发原版盾牌破防后，也会终止当前武器格挡窗口并共享盾牌冷却。
- 蓄力取消时立即停止格挡；正常挥砍则在完整攻击动画结束时停止。
- 双持攻击会让实际执行该次攻击的主手或副手武器承担格挡耐久。

玩家不需要按新的按键。安装附属模组后功能自动生效。

## 兼容范围

- Minecraft 1.19.2
- Java 17
- Better Combat 1.7.1+1.19
- Fabric Loader 0.14.9 及 Fabric API
- Forge 43.1+

附属模组针对 Better Combat 1.19.2 分支的 1.7.1 网络与攻击 API 实现，并在元数据中限制了对应版本范围。只有 Better Combat 已识别并分配武器属性的物品会获得挥砍格挡。

## 构建

在项目根目录执行：

    .\gradlew.bat build

产物位于：

    fabric\build\libs\bettercombat-swing-guard-fabric-1.0.0+1.19.2.jar
    forge\build\libs\bettercombat-swing-guard-forge-1.0.0+1.19.2.jar

运行时仍需安装 Better Combat 自身要求的 Fabric API（Fabric）、Cloth Config 与 playerAnimator。

## 实现说明

Better Combat 在客户端开始攻击动画时会向服务端发送 AttackAnimation 数据包，但 1.7.1 服务端原本只转发动画，不保存挥砍状态。本附属模组包装同一个接收器，在服务端完成以下工作：

1. 验证发包玩家当前手持物确实有 Better Combat 武器属性。
2. 验证动画名、攻击手、双持或双手状态、动画时长和攻击冷却。
3. 使用服务端攻击速度计算格挡窗口，忽略数据包中的玩家实体编号。
4. 将有效窗口接入 LivingEntity.blockedByShield 的原版判定。

这避免了额外客户端计时器与服务端计时器漂移，也不会修改 Better Combat 的攻击命中、连段、伤害或动画。
