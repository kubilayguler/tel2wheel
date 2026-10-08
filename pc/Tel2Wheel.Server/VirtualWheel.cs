using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

namespace Tel2Wheel.Server;

/// <summary>Virtual Xbox 360 pad: steering on LeftThumbX, throttle on RT, brake on LT.</summary>
public sealed class VirtualWheel : IDisposable
{
    private readonly ViGEmClient _client;
    private readonly IXbox360Controller _pad;

    public VirtualWheel()
    {
        _client = new ViGEmClient();
        _pad = _client.CreateXbox360Controller();
        _pad.AutoSubmitReport = false;
        _pad.Connect();
        Submit(0, 0, 0);
    }

    public void Submit(short steer, byte throttle, byte brake)
    {
        _pad.SetAxisValue(Xbox360Axis.LeftThumbX, steer);
        _pad.SetSliderValue(Xbox360Slider.RightTrigger, throttle);
        _pad.SetSliderValue(Xbox360Slider.LeftTrigger, brake);
        _pad.SubmitReport();
    }

    public void Dispose()
    {
        try { Submit(0, 0, 0); } catch { }
        try { _pad.Disconnect(); } catch { }
        _client.Dispose();
    }
}
