using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace Tel2Wheel.Server;

/// <summary>
/// Announces the server once per second on every IPv4 subnet so the phone can find it, whether
/// the phone is the hotspot or the PC is (Windows Mobile Hotspot).
/// </summary>
public sealed class BeaconBroadcaster : IDisposable
{
    private readonly Socket _socket;
    private readonly byte[] _beacon;
    private readonly Timer _timer;

    public BeaconBroadcaster(int inputPort)
    {
        _beacon = Protocol.BuildBeacon(inputPort, Environment.MachineName);
        _socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp) { EnableBroadcast = true };
        _timer = new Timer(_ => Send(), null, 0, 1000);
    }

    private void Send()
    {
        foreach (var target in BroadcastAddresses())
        {
            try { _socket.SendTo(_beacon, new IPEndPoint(target, Protocol.BeaconPort)); }
            catch (SocketException) { }
            catch (ObjectDisposedException) { return; }
        }
    }

    private static IEnumerable<IPAddress> BroadcastAddresses()
    {
        var seen = new HashSet<IPAddress>();
        NetworkInterface[] nics;
        try { nics = NetworkInterface.GetAllNetworkInterfaces(); }
        catch (NetworkInformationException) { yield break; }

        foreach (var nic in nics)
        {
            if (nic.OperationalStatus != OperationalStatus.Up) continue;
            if (nic.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
            foreach (var ua in nic.GetIPProperties().UnicastAddresses)
            {
                if (ua.Address.AddressFamily != AddressFamily.InterNetwork || ua.IPv4Mask is null) continue;
                var ip = ua.Address.GetAddressBytes();
                var mask = ua.IPv4Mask.GetAddressBytes();
                var bc = new byte[4];
                for (int i = 0; i < 4; i++) bc[i] = (byte)(ip[i] | ~mask[i]);
                var addr = new IPAddress(bc);
                if (seen.Add(addr)) yield return addr;
            }
        }
        if (seen.Add(IPAddress.Broadcast)) yield return IPAddress.Broadcast;
    }

    public static IEnumerable<string> LocalAddresses() =>
        NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback)
            .SelectMany(n => n.GetIPProperties().UnicastAddresses
                .Where(a => a.Address.AddressFamily == AddressFamily.InterNetwork)
                .Select(a => $"{a.Address} ({n.Name})"));

    public void Dispose()
    {
        _timer.Dispose();
        _socket.Dispose();
    }
}
