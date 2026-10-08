package com.goofy.goofyaddons.config;

/** Physical-key migration from GLFW (26.1.2) to SDL scancodes (26.3). */
public final class LegacyKeys {
    private LegacyKeys(){}
    public static int fromGlfw(int code) {
        if(code>=65 && code<=90)return code-65+4;
        if(code>=49 && code<=57)return code-49+30;
        if(code>=290 && code<=301)return code-290+58;
        if(code>=302 && code<=313)return code-302+104;
        if(code>=321 && code<=329)return code-321+89;
        if(code>=340 && code<=347)return code-340+224;
        return switch(code) {
            case 32->44;case 39->52;case 44->54;case 45->45;case 46->55;case 47->56;case 48->39;
            case 59->51;case 61->46;case 91->47;case 92->49;case 93->48;case 96->53;
            case 256->41;case 257->40;case 258->43;case 259->42;case 260->73;case 261->76;
            case 262->79;case 263->80;case 264->81;case 265->82;case 266->75;case 267->78;case 268->74;case 269->77;
            case 280->57;case 281->71;case 282->83;case 283->70;case 284->72;case 320->98;
            case 330->99;case 331->84;case 332->85;case 333->86;case 334->87;case 335->88;case 336->103;case 348->118;
            default->throw new IllegalArgumentException("Unsupported old keyboard code "+code+"; choose another binding before migration");
        };
    }
}
