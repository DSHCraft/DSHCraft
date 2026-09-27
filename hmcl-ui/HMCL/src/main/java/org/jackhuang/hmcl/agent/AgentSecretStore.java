/*
 * DSHCraft direct HMCL fork
 * Copyright (C) 2026 DSHCraft contributors
 *
 * This file is licensed under the GNU General Public License, version 3 or later.
 */
package org.jackhuang.hmcl.agent;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/// Stores DSHCraft Provider keys and MCP tokens in separate Windows Credential Manager targets.
@NotNullByDefault
public final class AgentSecretStore {
    /// Credential Manager target prefix reserved for the GPL Java launcher.
    private static final String TARGET_PREFIX = "DSHCraft/provider/";
    /// Separate target namespace for MCP bearer tokens.
    private static final String MCP_TARGET_PREFIX = "DSHCraft/mcp/";
    /// Windows generic credential type.
    private static final int CRED_TYPE_GENERIC = 1;
    /// Persists the credential for this Windows user on this machine.
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
    /// Windows error returned when a credential does not exist.
    private static final int ERROR_NOT_FOUND = 1168;

    /// Prevents construction of a stateless credential service.
    private AgentSecretStore() {
    }

    /// Saves a nonblank Provider key without writing it to launcher configuration files.
    public static void set(String providerId, String secret) throws IOException {
        setTarget(target(providerId), secret);
    }

    /// Saves one MCP bearer token in a namespace separate from Provider keys.
    public static void setMcp(String serverId, String secret) throws IOException {
        setTarget(mcpTarget(serverId), secret);
    }

    /// Writes a credential to Windows Credential Manager without persisting plaintext files.
    private static void setTarget(String target, String secret) throws IOException {
        if (secret.isEmpty()) {
            deleteTarget(target);
            return;
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_16LE);
        Memory blob = new Memory(bytes.length);
        try {
            blob.write(0, bytes, 0, bytes.length);
            Credential credential = new Credential();
            credential.Type = CRED_TYPE_GENERIC;
            credential.TargetName = new WString(target);
            credential.CredentialBlobSize = bytes.length;
            credential.CredentialBlob = blob;
            credential.Persist = CRED_PERSIST_LOCAL_MACHINE;
            credential.UserName = new WString("DSHCraft");
            credential.write();
            if (!api().CredWrite(credential, 0)) {
                throw new IOException("Windows Credential Manager rejected the Provider key: " + Native.getLastError());
            }
        } finally {
            blob.clear();
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /// Reads a Provider key only when launching or testing that Provider.
    public static @Nullable String get(String providerId) throws IOException {
        return getTarget(target(providerId));
    }

    /// Reads one MCP bearer token only when preparing that server's instance launch.
    public static @Nullable String getMcp(String serverId) throws IOException {
        return getTarget(mcpTarget(serverId));
    }

    /// Reads a credential from Windows Credential Manager.
    private static @Nullable String getTarget(String target) throws IOException {
        PointerByReference address = new PointerByReference();
        if (!api().CredRead(new WString(target), CRED_TYPE_GENERIC, 0, address)) {
            int error = Native.getLastError();
            if (error == ERROR_NOT_FOUND) return null;
            throw new IOException("Windows Credential Manager could not read the Provider key: " + error);
        }
        Pointer pointer = address.getValue();
        try {
            Credential credential = new Credential(pointer);
            if (credential.CredentialBlobSize < 0 || credential.CredentialBlobSize > 5120
                    || credential.CredentialBlobSize % 2 != 0) {
                throw new IOException("Windows Credential Manager returned an invalid Provider key");
            }
            byte[] bytes = credential.CredentialBlob.getByteArray(0, credential.CredentialBlobSize);
            try {
                return new String(bytes, StandardCharsets.UTF_16LE);
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        } finally {
            api().CredFree(pointer);
        }
    }

    /// Reports whether the current user has saved a key for the Provider.
    public static boolean has(String providerId) throws IOException {
        return get(providerId) != null;
    }

    /// Reports whether an MCP server has a bearer token in the OS credential store.
    public static boolean hasMcp(String serverId) throws IOException {
        return getMcp(serverId) != null;
    }

    /// Uses the OS credential first, then the configured environment variable for compatibility.
    public static @Nullable String resolve(AgentProvider provider) throws IOException {
        if (isSupported()) {
            String stored = get(provider.getId());
            if (stored != null && !stored.isBlank()) return stored;
        }
        String variable = provider.apiKeyEnvProperty().get();
        return variable == null || variable.isBlank() ? null : System.getenv(variable.trim());
    }

    /// Reports whether the current platform has this implementation of OS credential storage.
    public static boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /// Removes a Provider key while tolerating an already absent entry.
    public static void delete(String providerId) throws IOException {
        deleteTarget(target(providerId));
    }

    /// Deletes one MCP bearer token without touching Provider credentials.
    public static void deleteMcp(String serverId) throws IOException {
        deleteTarget(mcpTarget(serverId));
    }

    /// Deletes a credential while tolerating an already absent target.
    private static void deleteTarget(String target) throws IOException {
        if (!api().CredDelete(new WString(target), CRED_TYPE_GENERIC, 0)) {
            int error = Native.getLastError();
            if (error != ERROR_NOT_FOUND) {
                throw new IOException("Windows Credential Manager could not delete the Provider key: " + error);
            }
        }
    }

    /// Validates an ID before deriving the Credential Manager target name.
    private static String target(String providerId) throws IOException {
        if (providerId == null || !providerId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IOException("Invalid Provider ID for Credential Manager");
        }
        return TARGET_PREFIX + providerId;
    }

    /// Validates a server ID before deriving its separate Credential Manager target.
    private static String mcpTarget(String serverId) throws IOException {
        if (serverId == null || !serverId.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IOException("Invalid MCP server ID for Credential Manager");
        }
        return MCP_TARGET_PREFIX + serverId;
    }

    /// Loads the native API only on Windows so other platforms fail explicitly.
    private static CredentialApi api() throws IOException {
        if (!isSupported()) {
            throw new IOException("OS credential storage is not yet available on this platform");
        }
        return CredentialApi.INSTANCE;
    }

    /// Windows FILETIME layout embedded in a CREDENTIALW structure.
    @Structure.FieldOrder({"low", "high"})
    public static final class FileTime extends Structure {
        /// Low 32 bits of the Windows timestamp.
        public int low;
        /// High 32 bits of the Windows timestamp.
        public int high;
    }

    /// JNA view of the Windows CREDENTIALW structure.
    @Structure.FieldOrder({"Flags", "Type", "TargetName", "Comment", "LastWritten",
            "CredentialBlobSize", "CredentialBlob", "Persist", "AttributeCount", "Attributes",
            "TargetAlias", "UserName"})
    public static final class Credential extends Structure {
        /// Credential flags.
        public int Flags;
        /// Credential type.
        public int Type;
        /// Credential target name.
        public @Nullable WString TargetName;
        /// Optional comment.
        public @Nullable WString Comment;
        /// Last-write timestamp.
        public FileTime LastWritten = new FileTime();
        /// Credential secret size in bytes.
        public int CredentialBlobSize;
        /// Pointer to the credential secret bytes.
        public @Nullable Pointer CredentialBlob;
        /// Persistence scope.
        public int Persist;
        /// Number of attributes.
        public int AttributeCount;
        /// Optional attributes pointer.
        public @Nullable Pointer Attributes;
        /// Optional alias.
        public @Nullable WString TargetAlias;
        /// Credential user name.
        public @Nullable WString UserName;

        /// Allocates a credential structure for CredWrite.
        public Credential() {
        }

        /// Reads a credential structure returned by CredRead.
        public Credential(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    /// Native Credential Manager API entry points.
    private interface CredentialApi extends StdCallLibrary {
        /// Native API binding for the current process.
        CredentialApi INSTANCE = Native.load("Advapi32", CredentialApi.class, W32APIOptions.UNICODE_OPTIONS);

        /// Stores one generic credential.
        boolean CredWrite(Credential credential, int flags);

        /// Loads one generic credential.
        boolean CredRead(WString target, int type, int flags, PointerByReference credential);

        /// Deletes one generic credential.
        boolean CredDelete(WString target, int type, int flags);

        /// Frees the native structure returned by CredRead.
        void CredFree(Pointer credential);
    }
}
