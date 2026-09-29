package com.cheatbreaker.client.config;

import com.cheatbreaker.client.CheatBreaker;
import com.cheatbreaker.client.module.AbstractModule;
import com.cheatbreaker.client.ui.module.CBGuiAnchor;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

public class ConfigManager {

    public static final File configDir;
    public static final File profilesDir;
    private static final File globalConfig;

    static {
        configDir = new File(Minecraft.getMinecraft().mcDataDir + File.separator + "config" + File.separator + "client");
        profilesDir = new File(configDir + File.separator + "profiles");
        globalConfig = new File(configDir + File.separator + "global.cfg");
    }

    public void write() {
        try {
            if (this.createRequiredFiles()) {
                this.writeGlobalConfig(globalConfig);
                this.writeProfile(CheatBreaker.getInstance().activeProfile.getName());
            }
        } catch (IOException iOException) {
            iOException.printStackTrace();
        }
    }

    public void read() {
        try {
            if (this.createRequiredFiles()) {
                this.readGlobalConfig(globalConfig);
                if (CheatBreaker.getInstance().activeProfile == null) {
                    CheatBreaker.getInstance().activeProfile = CheatBreaker.getInstance().profiles.get(0);
                }
                this.readProfile(CheatBreaker.getInstance().activeProfile.getName());
            }
        } catch (IOException iOException) {
            iOException.printStackTrace();
        }
    }

    private boolean createRequiredFiles() throws IOException {
        return !(!configDir.exists() && !configDir.mkdirs() || !globalConfig.exists() && !globalConfig.createNewFile());
    }

    public void readGlobalConfig(File file) {
        if (!file.exists()) {
            this.writeGlobalConfig(file);
            return;
        }
        try {
            String line;
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            while ((line = bufferedReader.readLine()) != null) {
                try {
                    File profileFile;
                    String[] split;
                    if (line.startsWith("#") || line.length() == 0 || (split = line.split("=", 2)).length != 2)
                        continue;
                    if (split[0].equalsIgnoreCase("ProfileIndexes")) {
                        for (String declaration : split[1].split("]\\[")) {
                            declaration = declaration.replaceFirst("\\[", "");
                            String[] pair = declaration.split(",", 2);
                            try {
                                int index = Integer.parseInt(pair[1]);
                                for (Profile profile : CheatBreaker.getInstance().profiles) {
                                    if (index == 0 || !profile.getName().equalsIgnoreCase(pair[0])) continue;
                                    profile.setIndex(index);
                                }
                            } catch (NumberFormatException numberFormatException) {
                                // empty catch block
                            }
                        }
                        continue;
                    }
                    if (split[0].equalsIgnoreCase("ActiveProfile")) {
                        profileFile = null;
                        File profilesDir = new File(configDir + File.separator + "profiles");
                        if (profilesDir.exists() || profilesDir.mkdirs()) {
                            profileFile = new File(profilesDir + File.separator + split[1] + ".cfg");
                        }
                        if (profileFile == null || !profileFile.exists()) continue;
                        Profile activeProfile = null;
                        for (Profile profile : CheatBreaker.getInstance().profiles) {
                            if (!split[1].equalsIgnoreCase((profile).getName())) continue;
                            activeProfile = profile;
                        }
                        if (activeProfile == null || !activeProfile.isEditable()) continue;
                        CheatBreaker.getInstance().activeProfile = activeProfile;
                        continue;
                    }
                    for (Setting setting : CheatBreaker.getInstance().globalSettings.settingsList) {
                        if (setting.getLabel().equalsIgnoreCase("label") || !setting.getLabel().equalsIgnoreCase(split[0]))
                            continue;

                        applySetting(setting, split[1], false);
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
            bufferedReader.close();
        } catch (IOException iOException) {
            iOException.printStackTrace();
        }
    }

    public void writeGlobalConfig(File file) {
        writeAtomically(file, bufferedWriter -> {
            bufferedWriter.write("################################");
            bufferedWriter.newLine();
            bufferedWriter.write("# MC_Client: GLOBAL SETTINGS");
            bufferedWriter.newLine();
            bufferedWriter.write("################################");
            bufferedWriter.newLine();
            bufferedWriter.newLine();
            if (CheatBreaker.getInstance().activeProfile != null && CheatBreaker.getInstance().activeProfile.isEditable()) {
                bufferedWriter.write("ActiveProfile=" + CheatBreaker.getInstance().activeProfile.getName());
                bufferedWriter.newLine();
            }
            for (Setting cBSetting : CheatBreaker.getInstance().globalSettings.settingsList) {
                if (cBSetting.getLabel().equalsIgnoreCase("label")) continue;
                if (cBSetting.rainbow) {
                    bufferedWriter.write(cBSetting.getLabel() + "=" + cBSetting.getValue() + ";rainbow");
                } else {
                    bufferedWriter.write(cBSetting.getLabel() + "=" + cBSetting.getValue());
                }
                bufferedWriter.newLine();
            }
            bufferedWriter.newLine();
            bufferedWriter.write("ProfileIndexes=");
            for (Profile profile : CheatBreaker.getInstance().profiles) {
                bufferedWriter.write("[" + profile.getName() + "," + profile.getIndex() + "]");
            }
            bufferedWriter.newLine();
        });
    }

    public void readProfile(String name) {
        if (name.equalsIgnoreCase("default")) {
            System.out.println("[CB] setting up default profile");
            for (AbstractModule module : CheatBreaker.getInstance().moduleManager.modules) {
                module.setState(module.defaultState);
                module.setAnchor(module.defaultGuiAnchor);
                module.setTranslations(module.defaultXTranslation, module.defaultYTranslation);
                module.setRenderHud(module.defaultRenderHud);
                for (int i = 0; i < module.getSettingsList().size(); ++i) {
                    try {
                        module.getSettingsList().get(i).rainbow = false;
                        module.getSettingsList().get(i).setValue(module.getDefaultSettingsValues().get(i), false);
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }
            return;
        }
        File file = new File(configDir + File.separator + "profiles");
        File file2 = file.exists() || file.mkdirs() ? new File(file + File.separator + name + ".cfg") : null;
        if (file2 == null || !file2.exists()) {
            this.writeProfile(name);
            return;
        }
        // Older profiles do not contain settings added by newer client versions.
        // Reset first so a missing key uses its default rather than the value
        // left behind by the previously active profile.
        for (AbstractModule module : CheatBreaker.getInstance().moduleManager.modules) {
            module.setState(module.defaultState);
            module.setAnchor(module.defaultGuiAnchor);
            module.setTranslations(module.defaultXTranslation, module.defaultYTranslation);
            module.setRenderHud(module.defaultRenderHud);
            for (int i = 0; i < module.getSettingsList().size(); ++i) {
                module.getSettingsList().get(i).rainbow = false;
                module.getSettingsList().get(i).setValue(module.getDefaultSettingsValues().get(i), false);
            }
        }
        ArrayList<AbstractModule> arrayList = new ArrayList<>(CheatBreaker.getInstance().moduleManager.modules);
        try {
            String line;
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file2));
            AbstractModule object = null;
            while ((line = bufferedReader.readLine()) != null) {
                try {
                    String[] split;
                    if (line.startsWith("#") || line.length() == 0) continue;
                    if (line.startsWith("[")) {
                        object = null;
                        for (AbstractModule module : arrayList) {
                            if (!("[" + module.getName() + "]").equalsIgnoreCase(line)) continue;
                            object = module;
                        }
                        continue;
                    }
                    if (object == null) continue;
                    if (line.startsWith("-")) {
                        split = line.replaceFirst("-", "").split("=", 2);
                        if (split.length != 2) continue;
                        try {
                            switch (split[0]) {
                                case "State": {
                                    if (object.isStaffModule()) break;
                                    Object value = ConfigValueCodec.parse(Setting.Type.BOOLEAN, split[1], null, null, null);
                                    if (value != null) object.setState((Boolean) value);
                                    break;
                                }
                                case "RenderHUD": {
                                    Object value = ConfigValueCodec.parse(Setting.Type.BOOLEAN, split[1], null, null, null);
                                    if (value != null) object.setRenderHud((Boolean) value);
                                    break;
                                }
                                case "Anchor": {
                                    object.setAnchor(CBGuiAnchor.valueOf(split[1]));
                                    break;
                                }
                                case "xTranslation": {
                                    Object value = ConfigValueCodec.parse(Setting.Type.FLOAT, split[1], null, null, null);
                                    if (value != null) object.setXTranslation((Float) value);
                                    break;
                                }
                                case "yTranslation": {
                                    Object value = ConfigValueCodec.parse(Setting.Type.FLOAT, split[1], null, null, null);
                                    if (value != null) object.setYTranslation((Float) value);
                                    break;
                                }
                            }
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                        continue;
                    }
                    split = line.split("=", 2);
                    if (split.length != 2) continue;
                    for (Setting setting : object.getSettingsList()) {
                        if (setting.getLabel().equalsIgnoreCase("label") || !setting.getLabel().equalsIgnoreCase(split[0]))
                            continue;

                        applySetting(setting, split[1], true);
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
            bufferedReader.close();
        } catch (IOException iOException) {
            iOException.printStackTrace();
        }
    }

    private static void applySetting(Setting setting, String stored, boolean profileSetting) {
        boolean rainbow = setting.getType() == Setting.Type.INTEGER && stored.endsWith(";rainbow");
        String raw = rainbow ? stored.substring(0, stored.length() - ";rainbow".length()) : stored;
        Object value = ConfigValueCodec.parse(setting.getType(), raw,
                setting.getMinimumValue(), setting.getMaximumValue(), setting.getAcceptedValues());
        if (value == null) return;
        if (profileSetting && setting.getType() == Setting.Type.STRING) {
            value = ((String) value).replaceAll("&([0-9a-fk-orA-FK-OR])", "\u00A7$1");
        }
        setting.setValue(value, false);
        if (setting.getType() == Setting.Type.INTEGER) setting.rainbow = rainbow;
    }

    private interface ConfigWriter {
        void write(BufferedWriter writer) throws IOException;
    }

    private static void writeAtomically(File target, ConfigWriter content) {
        File staged = null;
        try {
            staged = File.createTempFile(target.getName(), ".tmp", target.getParentFile());
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(staged))) {
                content.write(writer);
            }
            try {
                Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            error.printStackTrace();
        } finally {
            if (staged != null) staged.delete();
        }
    }

    public void writeProfile(String string) {
        if (string.equalsIgnoreCase("default")) {
            return;
        }
        File profilesDir = new File(configDir + File.separator + "profiles");
        File profileFile = profilesDir.exists() || profilesDir.mkdirs() ? new File(profilesDir + File.separator + string + ".cfg") : null;
        if (profileFile == null) {
            System.err.println("[CB] Config manager panic!");
            return;
        }
        ArrayList<AbstractModule> arrayList = new ArrayList<>(CheatBreaker.getInstance().moduleManager.modules);
        writeAtomically(profileFile, bufferedWriter -> {
            bufferedWriter.write("################################");
            bufferedWriter.newLine();
            bufferedWriter.write("# MC_Client: MODULE SETTINGS");
            bufferedWriter.newLine();
            bufferedWriter.write("################################");
            bufferedWriter.newLine();
            bufferedWriter.newLine();
            for (AbstractModule Module : arrayList) {
                bufferedWriter.write("[" + Module.getName() + "]");
                bufferedWriter.newLine();
                bufferedWriter.write("-State=" + Module.isEnabled());
                bufferedWriter.newLine();
                bufferedWriter.write("-Anchor=" + Module.getGuiAnchor());
                bufferedWriter.newLine();
                bufferedWriter.write("-xTranslation=" + Module.getXTranslation());
                bufferedWriter.newLine();
                bufferedWriter.write("-yTranslation=" + Module.getYTranslation());
                bufferedWriter.newLine();
                bufferedWriter.write("-RenderHUD=" + Module.isRenderHud());
                bufferedWriter.newLine();
                for (Setting cBSetting : Module.getSettingsList()) {
                    if (cBSetting.getLabel().equalsIgnoreCase("label")) continue;
                    if (cBSetting.getType() == Setting.Type.STRING) {
                        bufferedWriter.write(cBSetting.getLabel() + "=" + (cBSetting.getValue() + "").replace('\u00A7', '&'));
                    } else if (cBSetting.rainbow) {
                        bufferedWriter.write(cBSetting.getLabel() + "=" + cBSetting.getValue() + ";rainbow");
                    } else {
                        bufferedWriter.write(cBSetting.getLabel() + "=" + cBSetting.getValue());
                    }
                    bufferedWriter.newLine();
                }
                bufferedWriter.newLine();
            }
        });
    }

}
