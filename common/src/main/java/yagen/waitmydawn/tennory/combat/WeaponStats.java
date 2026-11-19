package yagen.waitmydawn.tennory.combat;

public record WeaponStats(double damage, double reach, double speed, double specialMultiplier) {
    public static final int DURABILITY = 2035;

    public static final WeaponStats GLAIVE = new WeaponStats(8.2, 2.5, 1.85, 7.2);
    public static final WeaponStats REDEEMER = new WeaponStats(10.6, 4.0, 1.52, 3.8);
    public static final WeaponStats QUASSUS = new WeaponStats(13.0, 3.4, 1.44, 13.2);
}
