using System.Buffers.Binary;

namespace Tel2Wheel.Server;

/// <summary>Wire format shared with the Android app. See protocol.md. All values little-endian.</summary>
public static class Protocol
{
    public const byte Magic = 0x57; // 'W'

    public const byte TypeInput = 1;
    public const byte TypePing = 2;
    public const byte TypePong = 3;
    public const byte TypeBeacon = 4;

    public const int DefaultPort = 47800;
    public const int BeaconPort = 47801;

    public const int InputLength = 12;
    public const int PingLength = 10;

    // INPUT: [0]=magic [1]=type [2..3]=u16 seq [4..5]=i16 steer [6]=u8 throttle [7]=u8 brake [8]=u8 flags [9..11]=reserved
    public static ushort ReadSeq(ReadOnlySpan<byte> p) => BinaryPrimitives.ReadUInt16LittleEndian(p.Slice(2));
    public static short ReadSteer(ReadOnlySpan<byte> p) => BinaryPrimitives.ReadInt16LittleEndian(p.Slice(4));
    public static byte ReadThrottle(ReadOnlySpan<byte> p) => p[6];
    public static byte ReadBrake(ReadOnlySpan<byte> p) => p[7];

    public static void WriteInput(Span<byte> p, ushort seq, short steer, byte throttle, byte brake)
    {
        p.Slice(0, InputLength).Clear();
        p[0] = Magic;
        p[1] = TypeInput;
        BinaryPrimitives.WriteUInt16LittleEndian(p.Slice(2), seq);
        BinaryPrimitives.WriteInt16LittleEndian(p.Slice(4), steer);
        p[6] = throttle;
        p[7] = brake;
    }

    /// <summary>True when <paramref name="seq"/> is newer than <paramref name="last"/>, wraparound-aware.</summary>
    public static bool IsNewer(ushort seq, ushort last) => (short)(seq - last) > 0;

    // BEACON: [0]=magic [1]=type [2..3]=u16 input port [4..]=UTF-8 server name
    public static byte[] BuildBeacon(int inputPort, string name)
    {
        var nameBytes = System.Text.Encoding.UTF8.GetBytes(name);
        if (nameBytes.Length > 64) nameBytes = nameBytes[..64];
        var p = new byte[4 + nameBytes.Length];
        p[0] = Magic;
        p[1] = TypeBeacon;
        BinaryPrimitives.WriteUInt16LittleEndian(p.AsSpan(2), (ushort)inputPort);
        nameBytes.CopyTo(p, 4);
        return p;
    }
}
