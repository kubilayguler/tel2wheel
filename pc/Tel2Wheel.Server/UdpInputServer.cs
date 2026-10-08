using System.Diagnostics;
using System.Net;
using System.Net.Sockets;

namespace Tel2Wheel.Server;

/// <summary>
/// Receives INPUT packets and forwards each one straight to the virtual pad on a dedicated
/// high-priority thread. No queues, no allocations on the hot path. Echoes PINGs as PONGs.
/// </summary>
public sealed class UdpInputServer : IDisposable
{
    private const int SioUdpConnReset = -1744830452; // _WSAIOW(IOC_VENDOR, 12)
    private static readonly long WatchdogTicks = Stopwatch.Frequency / 4; // 250 ms

    private readonly Socket _socket;
    private readonly VirtualWheel _wheel;
    private readonly Thread _thread;
    private volatile bool _running = true;

    private SocketAddress? _client;
    private ushort _lastSeq;
    private long _lastPacketTicks;
    private bool _neutral = true;

    // Stats, read by the status printer.
    public long Packets;
    public long Lost;
    public long Stale;
    public volatile string ClientName = "-";
    public volatile int LastSteer, LastThrottle, LastBrake;
    public bool Connected => !_neutral;

    public UdpInputServer(int port, VirtualWheel wheel)
    {
        _wheel = wheel;
        _socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
        // Otherwise an ICMP "port unreachable" (e.g. pong to a closed app) kills the next Receive.
        _socket.IOControl(SioUdpConnReset, [0, 0, 0, 0], null);
        _socket.ReceiveBufferSize = 64 * 1024;
        _socket.Bind(new IPEndPoint(IPAddress.Any, port));

        _thread = new Thread(Run) { IsBackground = true, Name = "udp-input", Priority = ThreadPriority.Highest };
        _thread.Start();
    }

    private void Run()
    {
        var buf = new byte[256];
        var addr = new SocketAddress(AddressFamily.InterNetwork);

        while (_running)
        {
            int len;
            try
            {
                // 50 ms poll so the watchdog can fire while the phone is silent.
                if (!_socket.Poll(50_000, SelectMode.SelectRead))
                {
                    CheckWatchdog();
                    continue;
                }
                len = _socket.ReceiveFrom(buf, SocketFlags.None, addr);
            }
            catch (SocketException) { continue; }
            catch (ObjectDisposedException) { return; }

            if (len < 2 || buf[0] != Protocol.Magic) continue;

            switch (buf[1])
            {
                case Protocol.TypeInput when len >= Protocol.InputLength:
                    HandleInput(buf, addr);
                    break;
                case Protocol.TypePing when len >= Protocol.PingLength:
                    buf[1] = Protocol.TypePong;
                    try { _socket.SendTo(buf.AsSpan(0, len), SocketFlags.None, addr); } catch (SocketException) { }
                    break;
            }
        }
    }

    private void HandleInput(byte[] buf, SocketAddress addr)
    {
        long now = Stopwatch.GetTimestamp();
        ushort seq = Protocol.ReadSeq(buf);

        // A new phone, or the same one after a pause: accept whatever sequence it starts with.
        bool fresh = _client is null || _neutral || !addr.Equals(_client);
        if (!fresh)
        {
            if (!Protocol.IsNewer(seq, _lastSeq))
            {
                Stale++;
                return;
            }
            int gap = (ushort)(seq - _lastSeq) - 1;
            if (gap > 0) Lost += gap;
        }
        else if (_client is null || !addr.Equals(_client))
        {
            _client = Clone(addr);
            ClientName = new IPEndPoint(IPAddress.Any, 0).Create(addr).ToString() ?? "?";
        }

        _lastSeq = seq;
        _lastPacketTicks = now;
        _neutral = false;

        short steer = Protocol.ReadSteer(buf);
        byte throttle = Protocol.ReadThrottle(buf);
        byte brake = Protocol.ReadBrake(buf);
        _wheel.Submit(steer, throttle, brake);

        Packets++;
        LastSteer = steer;
        LastThrottle = throttle;
        LastBrake = brake;
    }

    private void CheckWatchdog()
    {
        if (_neutral) return;
        if (Stopwatch.GetTimestamp() - _lastPacketTicks < WatchdogTicks) return;
        _neutral = true;
        _wheel.Submit(0, 0, 0);
        LastSteer = LastThrottle = LastBrake = 0;
    }

    private static SocketAddress Clone(SocketAddress a)
    {
        var c = new SocketAddress(a.Family, a.Size);
        a.Buffer.Span[..a.Size].CopyTo(c.Buffer.Span);
        return c;
    }

    public void Dispose()
    {
        _running = false;
        _socket.Dispose();
        _thread.Join(500);
    }
}
