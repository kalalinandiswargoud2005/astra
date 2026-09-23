import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Shield, 
  Crosshair, 
  Radio, 
  Terminal as TerminalIcon, 
  Volume2, 
  VolumeX, 
  AlertTriangle, 
  Zap, 
  Server, 
  Laptop, 
  Cpu, 
  Activity, 
  Lock, 
  Unlock, 
  Play, 
  Pause, 
  Trash2, 
  ChevronRight, 
  Layers, 
  Maximize2,
  CheckCircle2,
  Sliders
} from 'lucide-react';
import { tacticalAudio } from '@/utils/tacticalSound';

// ── Types ───────────────────────────────────────────────────────────────────
type DefconLevel = 1 | 2 | 3 | 4 | 5;

interface LogEntry {
  id: string;
  timestamp: string;
  category: 'DETECT' | 'DEFEND' | 'DEFEAT' | 'DEFCON' | 'SYSTEM';
  message: string;
  source: string;
  latency?: string;
}

// ── Exported Background Utilities (used by IdleGlobeOverlay) ─────────────────
export function ParticleField() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId: number;
    let W = (canvas.width = window.innerWidth);
    let H = (canvas.height = window.innerHeight);

    const resize = () => {
      W = canvas.width = window.innerWidth;
      H = canvas.height = window.innerHeight;
    };
    window.addEventListener('resize', resize);

    const count = 70;
    const particles = Array.from({ length: count }, () => ({
      x: Math.random() * W,
      y: Math.random() * H,
      vx: (Math.random() - 0.5) * 0.3,
      vy: (Math.random() - 0.5) * 0.3,
      r: Math.random() * 1.5 + 0.3,
      alpha: Math.random() * 0.5 + 0.1,
      decay: (Math.random() - 0.5) * 0.002,
    }));

    const draw = () => {
      ctx.fillStyle = 'rgba(6, 9, 14, 0.25)';
      ctx.fillRect(0, 0, W, H);

      particles.forEach((p, i) => {
        p.x += p.vx;
        p.y += p.vy;
        p.alpha += p.decay;
        if (p.alpha <= 0.05) p.decay = Math.abs(p.decay);
        if (p.alpha >= 0.55) p.decay = -Math.abs(p.decay);
        if (p.x < 0) p.x = W; if (p.x > W) p.x = 0;
        if (p.y < 0) p.y = H; if (p.y > H) p.y = 0;

        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(0, 229, 255, ${p.alpha})`;
        ctx.fill();

        for (let j = i + 1; j < particles.length; j++) {
          const q = particles[j];
          const dx = p.x - q.x, dy = p.y - q.y;
          const dist = Math.sqrt(dx * dx + dy * dy);
          if (dist < 120) {
            const strength = (1 - dist / 120) * 0.12;
            ctx.beginPath();
            ctx.moveTo(p.x, p.y);
            ctx.lineTo(q.x, q.y);
            ctx.strokeStyle = `rgba(0, 229, 255, ${strength})`;
            ctx.lineWidth = 0.5;
            ctx.stroke();
          }
        }
      });

      animId = requestAnimationFrame(draw);
    };

    draw();
    return () => {
      cancelAnimationFrame(animId);
      window.removeEventListener('resize', resize);
    };
  }, []);

  return <canvas ref={canvasRef} className="absolute inset-0 w-full h-full z-0 pointer-events-none" />;
}

export function MouseSpotlight() {
  const [pos, setPos] = useState({ x: -1000, y: -1000 });

  useEffect(() => {
    const move = (e: MouseEvent) => setPos({ x: e.clientX, y: e.clientY });
    window.addEventListener('mousemove', move);
    return () => window.removeEventListener('mousemove', move);
  }, []);

  return (
    <div
      className="pointer-events-none fixed inset-0 z-[1]"
      style={{
        background: `radial-gradient(500px circle at ${pos.x}px ${pos.y}px, rgba(0,229,255,0.04) 0%, transparent 70%)`,
        transition: 'background 0.1s ease',
      }}
    />
  );
}

export function PulseRings() {
  return (
    <div className="absolute inset-0 flex items-center justify-center pointer-events-none z-[1]">
      {[1, 2, 3].map((i) => (
        <div
          key={i}
          className="absolute rounded-full border border-cyan-500/10 animate-ping"
          style={{
            width: `${200 + i * 140}px`,
            height: `${200 + i * 140}px`,
            animationDuration: `${3 + i}s`,
          }}
        />
      ))}
    </div>
  );
}

// ── Animated Radar Canvas Component ─────────────────────────────────────────
function RadarCanvas() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId: number;
    let angle = 0;
    const size = 180;
    canvas.width = size;
    canvas.height = size;
    const center = size / 2;

    // Simulated blips
    const blips = [
      { dist: 45, angle: 0.8, isHostile: false, size: 3 },
      { dist: 65, angle: 2.3, isHostile: true, size: 4 },
      { dist: 30, angle: 4.1, isHostile: false, size: 3 },
      { dist: 75, angle: 5.4, isHostile: false, size: 3.5 },
    ];

    const render = () => {
      ctx.clearRect(0, 0, size, size);

      // Outer & Inner concentric range rings
      ctx.strokeStyle = 'rgba(0, 229, 255, 0.2)';
      ctx.lineWidth = 1;
      [25, 45, 65, 82].forEach((r) => {
        ctx.beginPath();
        ctx.arc(center, center, r, 0, Math.PI * 2);
        ctx.stroke();
      });

      // Crosshairs
      ctx.strokeStyle = 'rgba(0, 229, 255, 0.15)';
      ctx.beginPath();
      ctx.moveTo(center, 4);
      ctx.lineTo(center, size - 4);
      ctx.moveTo(4, center);
      ctx.lineTo(size - 4, center);
      ctx.stroke();

      // Sweeping Beam Gradient
      const sweepGradient = ctx.createRadialGradient(center, center, 0, center, center, 82);
      sweepGradient.addColorStop(0, 'rgba(0, 255, 136, 0.4)');
      sweepGradient.addColorStop(1, 'rgba(0, 255, 136, 0.0)');

      ctx.save();
      ctx.translate(center, center);
      ctx.rotate(angle);
      
      // Sweep sector (approx 45 degrees)
      ctx.beginPath();
      ctx.moveTo(0, 0);
      ctx.arc(0, 0, 82, -Math.PI / 4, 0);
      ctx.closePath();
      ctx.fillStyle = sweepGradient;
      ctx.fill();

      // Lead line
      ctx.beginPath();
      ctx.moveTo(0, 0);
      ctx.lineTo(82, 0);
      ctx.strokeStyle = '#00ff88';
      ctx.lineWidth = 1.5;
      ctx.shadowColor = '#00ff88';
      ctx.shadowBlur = 8;
      ctx.stroke();
      ctx.restore();

      // Render Blips
      blips.forEach((b) => {
        const bx = center + Math.cos(b.angle) * b.dist;
        const by = center + Math.sin(b.angle) * b.dist;

        // Blip glow
        ctx.beginPath();
        ctx.arc(bx, by, b.size, 0, Math.PI * 2);
        ctx.fillStyle = b.isHostile ? '#ff1744' : '#00ff88';
        ctx.shadowColor = b.isHostile ? '#ff1744' : '#00ff88';
        ctx.shadowBlur = 6;
        ctx.fill();

        // Hostile pulse ring
        if (b.isHostile) {
          ctx.beginPath();
          ctx.arc(bx, by, b.size + 4, 0, Math.PI * 2);
          ctx.strokeStyle = 'rgba(255, 23, 68, 0.4)';
          ctx.lineWidth = 1;
          ctx.stroke();
        }
      });

      angle += 0.035;
      if (angle >= Math.PI * 2) angle = 0;

      animId = requestAnimationFrame(render);
    };

    render();
    return () => cancelAnimationFrame(animId);
  }, []);

  return (
    <div className="relative flex items-center justify-center">
      <canvas ref={canvasRef} className="rounded-full bg-[#06090e]/80 border border-cyan-500/25 shadow-[0_0_20px_rgba(0,229,255,0.15)]" />
      <div className="absolute text-[9px] font-mono tracking-widest text-cyan-400/60 top-1">360° SWEEP</div>
      <div className="absolute text-[9px] font-mono text-emerald-400 bottom-1 flex items-center gap-1">
        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
        <span>RADAR LOCK</span>
      </div>
    </div>
  );
}

// ── Radial Shield Gauge Component ───────────────────────────────────────────
function ShieldGauge() {
  return (
    <div className="relative flex items-center justify-center w-[180px] h-[180px]">
      {/* Background ring */}
      <svg className="w-full h-full transform -rotate-90" viewBox="0 0 100 100">
        <circle
          cx="50"
          cy="50"
          r="42"
          stroke="rgba(0, 229, 255, 0.15)"
          strokeWidth="6"
          fill="transparent"
        />
        {/* Active shield progress */}
        <circle
          cx="50"
          cy="50"
          r="42"
          stroke="url(#shieldGrad)"
          strokeWidth="6"
          strokeDasharray="264"
          strokeDashoffset="0"
          strokeLinecap="round"
          fill="transparent"
          className="transition-all duration-1000"
        />
        <defs>
          <linearGradient id="shieldGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#00e5ff" />
            <stop offset="100%" stopColor="#00ff88" />
          </linearGradient>
        </defs>
      </svg>

      {/* Ticking outer marker ring */}
      <div className="absolute inset-2 border border-dashed border-cyan-500/20 rounded-full animate-spin-slow pointer-events-none" />

      {/* Center Shield Status */}
      <div className="absolute flex flex-col items-center justify-center text-center">
        <Shield className="w-7 h-7 text-cyan-400 animate-pulse mb-0.5" />
        <span className="text-xl font-black font-['Orbitron'] tracking-wider text-white">100%</span>
        <span className="text-[9px] uppercase tracking-widest text-cyan-400 font-mono">SHIELD INTEGRITY</span>
      </div>
    </div>
  );
}

// ── Main C4ISR Tactical Deck Homepage ───────────────────────────────────────
export function Landing() {
  const navigate = useNavigate();

  // State
  const [defcon, setDefcon] = useState<DefconLevel>(5);
  const [zuluTime, setZuluTime] = useState('');
  const [eventsPerSec, setEventsPerSec] = useState(1420);
  const [threatsNeutralized, setThreatsNeutralized] = useState(27);
  const [isMuted, setIsMuted] = useState(false);
  const [isLockdown, setIsLockdown] = useState(false);
  const [isKioskMode, setIsKioskMode] = useState(false);
  const [streamActive, setStreamActive] = useState(true);
  const [directiveStatus, setDirectiveStatus] = useState('DIRECTIVE: ALL 18 FLEET NODES NOMINAL // ZERO PENDING ANOMALIES // SENTRY ARMED');
  const [isolatedNodes, setIsolatedNodes] = useState<Record<string, boolean>>({});
  const [lastKillAlert, setLastKillAlert] = useState<string | null>(null);

  // Kinetic logs feed
  const [logs, setLogs] = useState<LogEntry[]>([
    { id: '1', timestamp: '23:40:12', category: 'DETECT', message: 'Memory scan completed on SENTRY-WIN-EXEC-01 (Clean)', source: 'AgentCore', latency: '12ms' },
    { id: '2', timestamp: '23:41:05', category: 'DEFEND', message: 'Inbound port probe rejected on TCP:445 from 192.168.1.104', source: 'FirewallGuard' },
    { id: '3', timestamp: '23:42:19', category: 'DEFEAT', message: 'Terminated rogue PID 8912 cryptominer.exe in 29ms', source: 'ProcessRemediation', latency: '29ms' },
    { id: '4', timestamp: '23:43:02', category: 'DETECT', message: 'SHA-256 binary validation passed for kernel drivers', source: 'IntegrityGuard' },
    { id: '5', timestamp: '23:43:44', category: 'DEFEND', message: 'Zero-trust privilege escalation check: Nominal', source: 'AstraDefense' },
  ]);

  const logContainerRef = useRef<HTMLDivElement>(null);

  // Zulu Clock ticker
  useEffect(() => {
    const updateZulu = () => {
      const now = new Date();
      const h = String(now.getUTCHours()).padStart(2, '0');
      const m = String(now.getUTCMinutes()).padStart(2, '0');
      const s = String(now.getUTCSeconds()).padStart(2, '0');
      setZuluTime(`${h}:${m}:${s}Z`);
    };
    updateZulu();
    const interval = setInterval(updateZulu, 1000);
    return () => clearInterval(interval);
  }, []);

  // Fluctuating Telemetry Events
  useEffect(() => {
    const interval = setInterval(() => {
      const jitter = Math.floor(Math.random() * 25) - 12;
      setEventsPerSec((prev) => Math.max(1390, Math.min(1460, prev + jitter)));
    }, 1500);
    return () => clearInterval(interval);
  }, []);

  // Auto-scroll logs
  useEffect(() => {
    if (logContainerRef.current) {
      logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
    }
  }, [logs]);

  // Periodic background telemetry events in log
  useEffect(() => {
    if (!streamActive) return;
    const interval = setInterval(() => {
      const mockEvents = [
        { category: 'DETECT' as const, message: 'Telemetry heartbeat verified from SENTRY-WIN-VAULT-02', source: 'HeartbeatBus', latency: '14ms' },
        { category: 'DEFEND' as const, message: 'Continuous zero-trust socket verification passed', source: 'SocketAudit' },
        { category: 'DETECT' as const, message: 'Sensor poll cycle complete (18/18 active)', source: 'SensorMesh', latency: '8ms' },
      ];
      const randomEvent = mockEvents[Math.floor(Math.random() * mockEvents.length)];
      const now = new Date();
      const timeStr = `${String(now.getUTCHours()).padStart(2, '0')}:${String(now.getUTCMinutes()).padStart(2, '0')}:${String(now.getUTCSeconds()).padStart(2, '0')}`;
      
      setLogs((prev) => [
        ...prev.slice(-40),
        {
          id: Math.random().toString(36).substring(7),
          timestamp: timeStr,
          category: randomEvent.category,
          message: randomEvent.message,
          source: randomEvent.source,
          latency: randomEvent.latency,
        },
      ]);
    }, 6000);
    return () => clearInterval(interval);
  }, [streamActive]);

  // Handle DEFCON change
  const handleDefconSelect = (level: DefconLevel) => {
    setDefcon(level);
    tacticalAudio.playDefconSound(level);
    const defconDescriptions = {
      5: 'DEFCON 5 // ALL FORCES NOMINAL (PEACETIME READINESS)',
      4: 'DEFCON 4 // ADVISORY SURVEILLANCE & INCREASED INTEL GATHERING',
      3: 'DEFCON 3 // ELEVATED THREAT LEVEL - WEAPONS/REMEDIATION ARMED',
      2: 'DEFCON 2 // ARMED COMBAT READY - IMMEDIATE KINETIC INTERCEPTION',
      1: 'DEFCON 1 // MAXIMUM HOSTILE ENGAGEMENT - ACTIVE FLEET DEFENSE',
    };
    const desc = defconDescriptions[level];
    setDirectiveStatus(`DEFCON UPDATED: ${desc}`);

    const now = new Date();
    const timeStr = `${String(now.getUTCHours()).padStart(2, '0')}:${String(now.getUTCMinutes()).padStart(2, '0')}:${String(now.getUTCSeconds()).padStart(2, '0')}`;
    setLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(36).substring(7),
        timestamp: timeStr,
        category: 'DEFCON',
        message: `Command posture adjusted to DEFCON ${level} (${level === 1 ? 'HOSTILE ENGAGEMENT' : level === 2 ? 'ARMED' : level === 3 ? 'ELEVATED' : 'NOMINAL'})`,
        source: 'CommandDeck',
      },
    ]);
  };

  // Toggle Mute
  const handleToggleMute = () => {
    const nextMuted = !isMuted;
    setIsMuted(nextMuted);
    tacticalAudio.setMuted(nextMuted);
    if (!nextMuted) {
      tacticalAudio.playClick();
    }
  };

  // Command 1: Simulate Threat & Auto-Defeat
  const handleSimulateDefeat = () => {
    tacticalAudio.playDefeatSound();
    setThreatsNeutralized((prev) => prev + 1);
    
    const roguePids = [7420, 8912, 4516, 9208, 6134];
    const threatNames = ['Trojan.PowerShell.Stager', 'Ransom.CryptLock.v3', 'Mimikatz.LSA.Dump', 'Beacon.C2.ReverseTCP', 'Miner.XMRig.Injection'];
    const selectedPid = roguePids[Math.floor(Math.random() * roguePids.length)];
    const selectedThreat = threatNames[Math.floor(Math.random() * threatNames.length)];
    
    setLastKillAlert(`KINETIC INTERCEPTION: SIGKILL PID ${selectedPid} [${selectedThreat}] IN 24ms`);
    setDirectiveStatus(`DIRECTIVE: HOSTILE ENGAGED & TERMINATED // PID ${selectedPid} [${selectedThreat}] QUARANTINED`);

    setTimeout(() => {
      setLastKillAlert(null);
    }, 4000);

    const now = new Date();
    const timeStr = `${String(now.getUTCHours()).padStart(2, '0')}:${String(now.getUTCMinutes()).padStart(2, '0')}:${String(now.getUTCSeconds()).padStart(2, '0')}`;
    
    // Add realistic 3-stage kinetic intercept sequence
    setLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(36).substring(7),
        timestamp: timeStr,
        category: 'DETECT',
        message: `Autonomous sensor alerted on suspicious process injection: PID ${selectedPid} [${selectedThreat}]`,
        source: 'SensorCore',
        latency: '8ms'
      },
      {
        id: Math.random().toString(36).substring(7),
        timestamp: timeStr,
        category: 'DEFEND',
        message: `Process memory isolated, outbound sockets clamped via ProcessRemediationService`,
        source: 'ZeroTrustPolicy'
      },
      {
        id: Math.random().toString(36).substring(7),
        timestamp: timeStr,
        category: 'DEFEAT',
        message: `KINETIC SIGKILL CONFIRMED: Process PID ${selectedPid} neutralized (< 24ms). Checksum logged to vault.`,
        source: 'ProcessRemediationService',
        latency: '24ms'
      }
    ]);
  };

  // Command 2: Fleet Lockdown
  const handleToggleLockdown = () => {
    const nextState = !isLockdown;
    setIsLockdown(nextState);
    if (nextState) {
      tacticalAudio.playLockdownSound();
      setDirectiveStatus('DIRECTIVE: STRICT FLEET ZERO-TRUST LOCKDOWN ENFORCED // ALL NON-CORE SOCKETS CLAMPED');
    } else {
      tacticalAudio.playClick();
      setDirectiveStatus('DIRECTIVE: ZERO-TRUST LOCKDOWN RELEASED // FLEET POLICIES RESTORED TO NOMINAL');
    }

    const now = new Date();
    const timeStr = `${String(now.getUTCHours()).padStart(2, '0')}:${String(now.getUTCMinutes()).padStart(2, '0')}:${String(now.getUTCSeconds()).padStart(2, '0')}`;
    setLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(36).substring(7),
        timestamp: timeStr,
        category: nextState ? 'DEFEAT' : 'DEFEND',
        message: nextState ? 'FLEET LOCKDOWN DIRECTIVE APPLIED: Zero-trust process firewall clamping enabled' : 'FLEET LOCKDOWN RELEASED: Standard whitelisting active',
        source: 'FleetOrchestrator'
      }
    ]);
  };

  // Node Isolate Toggle
  const handleToggleIsolate = (nodeId: string) => {
    tacticalAudio.playClick();
    const isCurrentlyIsolated = !!isolatedNodes[nodeId];
    setIsolatedNodes((prev) => ({ ...prev, [nodeId]: !isCurrentlyIsolated }));

    const now = new Date();
    const timeStr = `${String(now.getUTCHours()).padStart(2, '0')}:${String(now.getUTCMinutes()).padStart(2, '0')}:${String(now.getUTCSeconds()).padStart(2, '0')}`;
    setLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(36).substring(7),
        timestamp: timeStr,
        category: isCurrentlyIsolated ? 'DEFEND' : 'DEFEAT',
        message: isCurrentlyIsolated ? `Air-gap isolation lifted for node ${nodeId}` : `EMERGENCY AIR-GAP ISOLATION ENGAGED for node ${nodeId}`,
        source: 'NetworkPolicyManager'
      }
    ]);
  };

  // Dynamic DEFCON styling
  const defconTheme = {
    5: { border: 'border-emerald-500/30', glow: 'shadow-[0_0_20px_rgba(0,255,136,0.15)]', badge: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40', accent: '#00ff88' },
    4: { border: 'border-cyan-500/30', glow: 'shadow-[0_0_20px_rgba(0,229,255,0.15)]', badge: 'bg-cyan-500/20 text-cyan-400 border-cyan-500/40', accent: '#00e5ff' },
    3: { border: 'border-amber-500/30', glow: 'shadow-[0_0_20px_rgba(255,183,3,0.15)]', badge: 'bg-amber-500/20 text-amber-400 border-amber-500/40', accent: '#ffb703' },
    2: { border: 'border-orange-500/40', glow: 'shadow-[0_0_25px_rgba(255,145,0,0.25)]', badge: 'bg-orange-500/20 text-orange-400 border-orange-500/50', accent: '#ff9100' },
    1: { border: 'border-rose-500/60', glow: 'shadow-[0_0_35px_rgba(255,23,68,0.35)] animate-pulse', badge: 'bg-rose-500/30 text-rose-300 border-rose-500 animate-pulse', accent: '#ff1744' },
  }[defcon];

  return (
    <div className={`min-h-screen bg-[#06090e] text-slate-100 font-sans selection:bg-cyan-500/30 selection:text-cyan-200 relative overflow-x-hidden ${isKioskMode ? 'text-sm' : ''}`}>
      
      {/* ── Background Tactical Scanline & Micro-Grid ─────────────────────── */}
      <div 
        className="fixed inset-0 pointer-events-none z-0 opacity-25"
        style={{
          backgroundImage: `
            linear-gradient(to right, rgba(0, 229, 255, 0.05) 1px, transparent 1px),
            linear-gradient(to bottom, rgba(0, 229, 255, 0.05) 1px, transparent 1px)
          `,
          backgroundSize: '32px 32px'
        }}
      />
      <div 
        className="fixed inset-0 pointer-events-none z-0 opacity-15"
        style={{
          backgroundImage: 'repeating-linear-gradient(0deg, rgba(0,0,0,0.4) 0px, rgba(0,0,0,0.4) 1px, transparent 1px, transparent 3px)'
        }}
      />
      {/* Ambient glowing radial backdrop */}
      <div 
        className="fixed inset-0 pointer-events-none z-0 transition-all duration-700"
        style={{
          background: `radial-gradient(ellipse 70% 50% at 50% 10%, ${defconTheme.accent}12 0%, transparent 70%)`
        }}
      />

      {/* ── Top Notification Banner (Hostile alert popup) ──────────────────── */}
      {lastKillAlert && (
        <div className="fixed top-0 left-0 right-0 z-50 bg-rose-600/90 text-white font-mono text-center py-2 px-4 shadow-[0_0_30px_rgba(255,23,68,0.8)] border-b border-rose-400 flex items-center justify-center gap-3 animate-bounce">
          <Zap className="w-5 h-5 fill-current" />
          <span className="font-bold tracking-widest text-sm">{lastKillAlert}</span>
        </div>
      )}

      {/* ── 1. HEADER & COMMAND BAR ────────────────────────────────────────── */}
      <header className="relative z-50 !overflow-visible border-b border-cyan-500/20 bg-[#06090e]/90 backdrop-blur-xl px-4 lg:px-8 py-3">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-4">
          
          {/* Left: Brand Identity */}
          <div className="flex items-center gap-3.5">
            <div className="relative flex items-center justify-center w-11 h-11 rounded-lg bg-gradient-to-br from-cyan-500/20 to-emerald-500/10 border border-cyan-400/40 shadow-[0_0_15px_rgba(0,229,255,0.3)]">
              <Crosshair className="w-6 h-6 text-cyan-400 animate-spin-slow" />
              <Shield className="w-4 h-4 text-emerald-400 absolute" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-2xl font-black font-['Orbitron'] tracking-[0.25em] bg-gradient-to-r from-white via-cyan-200 to-cyan-400 bg-clip-text text-transparent">
                  ASTRA
                </span>
                <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-cyan-950/80 text-cyan-400 border border-cyan-500/30">
                  v4.8.2-SEC
                </span>
              </div>
              <p className="text-[10px] font-mono tracking-widest text-slate-400 uppercase">
                C4ISR AUTONOMOUS ENDPOINT DEFENSE
              </p>
            </div>
          </div>

          {/* Center: High-Visibility Glowing Pill Badge */}
          <div className="hidden md:flex items-center gap-2.5 px-4 py-1.5 rounded-full bg-slate-950/80 border border-cyan-500/30 shadow-[0_0_20px_rgba(0,229,255,0.15)] font-mono text-xs font-semibold tracking-wider">
            <span className="flex items-center gap-1.5 text-emerald-400">
              <span className="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_8px_#00ff88]" />
              DETECT
            </span>
            <span className="text-slate-600">•</span>
            <span className="flex items-center gap-1.5 text-cyan-400">
              <span className="w-2 h-2 rounded-full bg-cyan-400 shadow-[0_0_8px_#00e5ff]" />
              DEFEND
            </span>
            <span className="text-slate-600">•</span>
            <span className="flex items-center gap-1.5 text-rose-400">
              <span className="w-2 h-2 rounded-full bg-rose-500 shadow-[0_0_8px_#ff1744]" />
              DEFEAT
            </span>
          </div>

          {/* Right: Telemetry Clock, Clearance & Quick Nav */}
          <div className="flex items-center gap-3 font-mono text-xs">

            {/* Audio Toggle */}
            <button
              onClick={handleToggleMute}
              title={isMuted ? 'Unmute tactical audio' : 'Mute tactical audio'}
              className="p-2 rounded bg-slate-900/90 border border-cyan-500/20 text-slate-300 hover:text-cyan-400 hover:border-cyan-400/50 transition-colors"
            >
              {isMuted ? <VolumeX className="w-4 h-4 text-slate-500" /> : <Volume2 className="w-4 h-4 text-cyan-400" />}
            </button>

            {/* Zulu Clock */}
            <div className="px-2.5 py-1 rounded bg-[#0b121e] border border-cyan-500/20 text-cyan-300 flex items-center gap-1.5">
              <Radio className="w-3.5 h-3.5 text-cyan-400 animate-pulse" />
              <span className="font-mono tracking-wider">{zuluTime || '00:00:00Z'}</span>
            </div>

            {/* Sentry Engaged Badge */}
            <div className="hidden lg:flex items-center gap-1.5 px-2.5 py-1 rounded bg-emerald-950/40 border border-emerald-500/30 text-emerald-400">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" />
              <span className="text-[11px] font-bold">SENTRY ENGAGED</span>
            </div>

            {/* Clearance Badge */}
            <div className="hidden sm:block px-2.5 py-1 rounded bg-rose-950/30 border border-rose-500/30 text-rose-300 text-[10px] tracking-widest font-bold">
              TOP SECRET // L5 ACCESS
            </div>

            {/* Enter Console Button */}
            <button
              onClick={() => {
                tacticalAudio.playClick();
                navigate('/dashboard');
              }}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs uppercase tracking-wider font-['Orbitron'] shadow-[0_0_15px_rgba(0,229,255,0.4)] transition-all transform active:scale-95 cursor-pointer"
            >
              <span>CONSOLE</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </header>

      {/* ── 2. INTERACTIVE DEFCON THREAT READINESS STRIP ─────────────────────── */}
      <section className="relative z-10 border-b border-cyan-500/20 bg-[#090e17]/90 px-4 lg:px-8 py-2.5">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-xs font-mono tracking-widest text-slate-400 uppercase">
            <AlertTriangle className="w-4 h-4 text-amber-400 animate-pulse" />
            <span>THREAT READINESS POSTURE:</span>
          </div>

          <div className="grid grid-cols-5 gap-1.5 w-full md:w-auto font-mono text-xs">
            {([5, 4, 3, 2, 1] as DefconLevel[]).map((level) => {
              const isActive = defcon === level;
              const styles = {
                5: { label: 'DEFCON 5', sub: 'NOMINAL', color: 'hover:border-emerald-500 text-emerald-400', active: 'bg-emerald-500/20 border-emerald-400 text-emerald-300 shadow-[0_0_12px_rgba(0,255,136,0.3)]' },
                4: { label: 'DEFCON 4', sub: 'ADVISORY', color: 'hover:border-cyan-500 text-cyan-400', active: 'bg-cyan-500/20 border-cyan-400 text-cyan-300 shadow-[0_0_12px_rgba(0,229,255,0.3)]' },
                3: { label: 'DEFCON 3', sub: 'ELEVATED', color: 'hover:border-amber-500 text-amber-400', active: 'bg-amber-500/20 border-amber-400 text-amber-300 shadow-[0_0_12px_rgba(255,183,3,0.3)]' },
                2: { label: 'DEFCON 2', sub: 'ARMED', color: 'hover:border-orange-500 text-orange-400', active: 'bg-orange-500/20 border-orange-400 text-orange-300 shadow-[0_0_12px_rgba(255,145,0,0.4)]' },
                1: { label: 'DEFCON 1', sub: 'HOSTILE', color: 'hover:border-rose-500 text-rose-400', active: 'bg-rose-500/30 border-rose-500 text-rose-200 shadow-[0_0_20px_rgba(255,23,68,0.5)] animate-pulse' },
              }[level];

              return (
                <button
                  key={level}
                  onClick={() => handleDefconSelect(level)}
                  className={`px-3 py-1.5 rounded border transition-all text-center cursor-pointer ${
                    isActive
                      ? styles.active
                      : `bg-slate-900/60 border-slate-800 text-slate-400 ${styles.color}`
                  }`}
                >
                  <div className="font-bold tracking-wider">{styles.label}</div>
                  <div className="text-[9px] opacity-75">{styles.sub}</div>
                </button>
              );
            })}
          </div>
        </div>
      </section>

      {/* ── 3. MAIN TACTICAL TRIAD (DETECT • DEFEND • DEFEAT) ────────────────── */}
      <main className="relative z-10 max-w-7xl mx-auto px-4 lg:px-8 py-6 space-y-6">
        
        {/* The 3-Pillar Tactical Hero Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          
          {/* CARD 1: DETECT */}
          <div className="relative rounded-xl bg-[#0d1424]/75 backdrop-blur-md border border-emerald-500/30 p-5 shadow-[0_0_20px_rgba(0,255,136,0.08)] flex flex-col justify-between overflow-hidden group hover:border-emerald-400/60 transition-all">
            <div className="absolute top-0 right-0 w-24 h-24 bg-emerald-500/5 rounded-full blur-2xl pointer-events-none" />
            
            <div>
              <div className="flex items-center justify-between mb-3 border-b border-emerald-500/20 pb-2">
                <span className="text-xs font-mono font-bold tracking-widest text-emerald-400">
                  [ 01 // DETECT ]
                </span>
                <span className="flex items-center gap-1 text-[10px] font-mono text-emerald-300 bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-500/30">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" />
                  REAL-TIME SENSORS
                </span>
              </div>

              {/* Animated Radar Sweep */}
              <div className="my-2">
                <RadarCanvas />
              </div>

              {/* Hero Metric */}
              <div className="text-center my-3">
                <div className="text-3xl lg:text-4xl font-black font-['Orbitron'] tracking-tight text-white flex items-center justify-center gap-2">
                  <span>{eventsPerSec.toLocaleString()}</span>
                  <span className="text-xs font-mono text-emerald-400 font-normal">EVT/S</span>
                </div>
                <div className="text-[11px] font-mono text-slate-400 uppercase tracking-wider mt-0.5">
                  Telemetry Events Scanned / Sec
                </div>
              </div>
            </div>

            {/* Bottom Metrics Grid */}
            <div className="pt-3 border-t border-emerald-500/20 grid grid-cols-2 gap-2 text-[11px] font-mono">
              <div className="bg-[#080d16] p-2 rounded border border-emerald-500/15">
                <span className="text-slate-400 text-[10px] block">ACTIVE SENSORS</span>
                <span className="text-emerald-400 font-bold">18/18 Online</span>
              </div>
              <div className="bg-[#080d16] p-2 rounded border border-emerald-500/15">
                <span className="text-slate-400 text-[10px] block">AI ENGINE</span>
                <span className="text-emerald-300 font-bold">v4.8 Neural</span>
              </div>
              <div className="col-span-2 bg-[#080d16] p-2 rounded border border-emerald-500/15 flex justify-between items-center">
                <span className="text-slate-400 text-[10px]">TELEMETRY LATENCY</span>
                <span className="text-emerald-400 font-bold">14ms (Instant)</span>
              </div>
            </div>
          </div>

          {/* CARD 2: DEFEND */}
          <div className="relative rounded-xl bg-[#0d1424]/75 backdrop-blur-md border border-cyan-500/30 p-5 shadow-[0_0_20px_rgba(0,229,255,0.08)] flex flex-col justify-between overflow-hidden group hover:border-cyan-400/60 transition-all">
            <div className="absolute top-0 right-0 w-24 h-24 bg-cyan-500/5 rounded-full blur-2xl pointer-events-none" />

            <div>
              <div className="flex items-center justify-between mb-3 border-b border-cyan-500/20 pb-2">
                <span className="text-xs font-mono font-bold tracking-widest text-cyan-400">
                  [ 02 // DEFEND ]
                </span>
                <span className="flex items-center gap-1 text-[10px] font-mono text-cyan-300 bg-cyan-950/60 px-2 py-0.5 rounded border border-cyan-500/30">
                  <Lock className="w-3 h-3 text-cyan-400" />
                  POLICY SHIELD
                </span>
              </div>

              {/* Radial Shield Gauge */}
              <div className="my-2 flex justify-center">
                <ShieldGauge />
              </div>

              {/* Hero Metric */}
              <div className="text-center my-3">
                <div className="text-3xl lg:text-4xl font-black font-['Orbitron'] tracking-tight text-white flex items-center justify-center gap-2">
                  <span>142</span>
                  <span className="text-xs font-mono text-cyan-400 font-normal">POLICIES</span>
                </div>
                <div className="text-[11px] font-mono text-slate-400 uppercase tracking-wider mt-0.5">
                  Active Zero-Trust Policy Guards
                </div>
              </div>
            </div>

            {/* Bottom Metrics Grid */}
            <div className="pt-3 border-t border-cyan-500/20 grid grid-cols-2 gap-2 text-[11px] font-mono">
              <div className="bg-[#080d16] p-2 rounded border border-cyan-500/15">
                <span className="text-slate-400 text-[10px] block">FIREWALL BARRIER</span>
                <span className="text-cyan-400 font-bold">Enforced (In/Out)</span>
              </div>
              <div className="bg-[#080d16] p-2 rounded border border-cyan-500/15">
                <span className="text-slate-400 text-[10px] block">CHECKSUM LOCK</span>
                <span className="text-cyan-300 font-bold">SHA-256 Valid</span>
              </div>
              <div className="col-span-2 bg-[#080d16] p-2 rounded border border-cyan-500/15 flex justify-between items-center">
                <span className="text-slate-400 text-[10px]">QUARANTINE VAULT</span>
                <span className="text-cyan-400 font-bold">3 Isolated Payloads</span>
              </div>
            </div>
          </div>

          {/* CARD 3: DEFEAT */}
          <div className="relative rounded-xl bg-[#0d1424]/75 backdrop-blur-md border border-rose-500/30 p-5 shadow-[0_0_20px_rgba(255,23,68,0.08)] flex flex-col justify-between overflow-hidden group hover:border-rose-400/60 transition-all">
            <div className="absolute top-0 right-0 w-24 h-24 bg-rose-500/5 rounded-full blur-2xl pointer-events-none" />

            <div>
              <div className="flex items-center justify-between mb-3 border-b border-rose-500/20 pb-2">
                <span className="text-xs font-mono font-bold tracking-widest text-rose-400">
                  [ 03 // DEFEAT ]
                </span>
                <span className="flex items-center gap-1 text-[10px] font-mono text-rose-300 bg-rose-950/60 px-2 py-0.5 rounded border border-rose-500/30">
                  <Zap className="w-3 h-3 text-rose-400 fill-current" />
                  KINETIC KILL
                </span>
              </div>

              {/* Kinetic Badge Display */}
              <div className="my-6 flex flex-col items-center justify-center min-h-[140px]">
                <div className="relative flex items-center justify-center">
                  <div className="w-24 h-24 rounded-full border-2 border-dashed border-rose-500/40 animate-reverse-spin" />
                  <div className="absolute w-20 h-20 rounded-full bg-gradient-to-br from-rose-600/30 to-rose-950/80 border border-rose-500/50 flex flex-col items-center justify-center shadow-[0_0_25px_rgba(255,23,68,0.3)]">
                    <Crosshair className="w-5 h-5 text-rose-400 mb-0.5" />
                    <span className="text-2xl font-black font-['Orbitron'] text-white">{threatsNeutralized}</span>
                  </div>
                </div>
                <div className="mt-3 text-xs font-mono font-bold text-rose-400 uppercase tracking-widest">
                  Hostile Threats Neutralized
                </div>
              </div>

              {/* Hero Metric Subtitle */}
              <div className="text-center mb-3">
                <div className="text-xs font-mono text-slate-300 font-bold">
                  Autonomous Remediation Service
                </div>
                <div className="text-[10px] font-mono text-rose-400/80 uppercase tracking-wider mt-0.5">
                  ProcessRemediationService // Autonomous Kill Reflex
                </div>
              </div>
            </div>

            {/* Bottom Metrics Grid */}
            <div className="pt-3 border-t border-rose-500/20 grid grid-cols-2 gap-2 text-[11px] font-mono">
              <div className="bg-[#080d16] p-2 rounded border border-rose-500/15">
                <span className="text-slate-400 text-[10px] block">AVG KILL LATENCY</span>
                <span className="text-rose-400 font-bold">&lt; 32ms</span>
              </div>
              <div className="bg-[#080d16] p-2 rounded border border-rose-500/15">
                <span className="text-slate-400 text-[10px] block">SOCKET SEVERANCE</span>
                <span className="text-rose-300 font-bold">Instant RST</span>
              </div>
              <div className="col-span-2 bg-[#080d16] p-2 rounded border border-rose-500/15 flex justify-between items-center">
                <span className="text-slate-400 text-[10px]">NEUTRALIZATION RATE</span>
                <span className="text-emerald-400 font-bold">100% Guaranteed</span>
              </div>
            </div>
          </div>

        </div>

        {/* ── 4. DUAL OPERATIONS SPLIT: FLEET NODES VS KINETIC LOG ─────────── */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          
          {/* Left Column (5 Cols): Theater of Operations // Fleet Sentry */}
          <div className="lg:col-span-5 space-y-4">
            <div className="flex items-center justify-between border-b border-cyan-500/20 pb-2">
              <div className="flex items-center gap-2 text-xs font-mono font-bold tracking-widest text-cyan-400">
                <Server className="w-4 h-4 text-cyan-400" />
                <span>THEATER OF OPERATIONS // FLEET SENTRY</span>
              </div>
              <span className="text-[10px] font-mono text-emerald-400">3 NODES CONNECTED</span>
            </div>

            {/* Node 1: SENTRY-WIN-EXEC-01 */}
            <div className={`p-3.5 rounded-lg bg-[#0a101b]/80 border transition-all ${
              isolatedNodes['WIN-01'] 
                ? 'border-rose-500/60 bg-rose-950/20 shadow-[0_0_15px_rgba(255,23,68,0.2)]' 
                : 'border-cyan-500/25 hover:border-cyan-500/50'
            }`}>
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2.5">
                  <Laptop className="w-5 h-5 text-cyan-400" />
                  <div>
                    <div className="text-xs font-bold font-mono text-white flex items-center gap-2">
                      <span>SENTRY-WIN-EXEC-01</span>
                      {isolatedNodes['WIN-01'] ? (
                        <span className="text-[9px] px-1.5 py-0.2 rounded bg-rose-500/30 text-rose-300 border border-rose-500/50">AIR-GAPPED</span>
                      ) : (
                        <span className="text-[9px] px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">ARMED</span>
                      )}
                    </div>
                    <div className="text-[10px] font-mono text-slate-400">Windows 11 Enterprise (23H2) • 192.168.1.45</div>
                  </div>
                </div>
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              </div>

              {/* Telemetry Bars */}
              <div className="mt-3 grid grid-cols-2 gap-2 text-[10px] font-mono">
                <div>
                  <div className="flex justify-between text-slate-400 mb-1">
                    <span>CPU LOAD</span>
                    <span className="text-cyan-400 font-bold">14.2%</span>
                  </div>
                  <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
                    <div className="h-full bg-cyan-400 rounded-full w-[14%]" />
                  </div>
                </div>
                <div>
                  <div className="flex justify-between text-slate-400 mb-1">
                    <span>MEMORY</span>
                    <span className="text-emerald-400 font-bold">4.8 / 16 GB</span>
                  </div>
                  <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
                    <div className="h-full bg-emerald-400 rounded-full w-[30%]" />
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="mt-3 pt-2.5 border-t border-slate-800/80 flex items-center justify-between gap-2">
                <button
                  onClick={() => {
                    tacticalAudio.playClick();
                    setDirectiveStatus('DIAGNOSTIC RUNNING: SENTRY-WIN-EXEC-01 zero-trust check complete (OK)');
                  }}
                  className="px-2.5 py-1 rounded bg-slate-900 border border-cyan-500/20 text-slate-300 hover:text-cyan-400 text-[10px] font-mono font-semibold uppercase tracking-wider cursor-pointer"
                >
                  DIAGNOSTIC
                </button>
                <button
                  onClick={() => handleToggleIsolate('WIN-01')}
                  className={`px-2.5 py-1 rounded text-[10px] font-mono font-semibold uppercase tracking-wider cursor-pointer transition-colors ${
                    isolatedNodes['WIN-01']
                      ? 'bg-rose-600 text-white border border-rose-400'
                      : 'bg-rose-950/50 hover:bg-rose-900/60 border border-rose-500/40 text-rose-300'
                  }`}
                >
                  {isolatedNodes['WIN-01'] ? 'LIFT ISOLATION' : 'AIR-GAP ISOLATE'}
                </button>
              </div>
            </div>

            {/* Node 2: SENTRY-WIN-VAULT-02 */}
            <div className="p-3.5 rounded-lg bg-[#0a101b]/80 border border-cyan-500/25 hover:border-cyan-500/50 transition-all">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2.5">
                  <Server className="w-5 h-5 text-cyan-400" />
                  <div>
                    <div className="text-xs font-bold font-mono text-white flex items-center gap-2">
                      <span>SENTRY-WIN-VAULT-02</span>
                      <span className="text-[9px] px-1.5 py-0.2 rounded bg-cyan-500/20 text-cyan-400 border border-cyan-500/30">CORE DB</span>
                    </div>
                    <div className="text-[10px] font-mono text-slate-400">Windows Server 2022 • 192.168.1.12</div>
                  </div>
                </div>
                <span className="w-2 h-2 rounded-full bg-emerald-400" />
              </div>

              <div className="mt-2.5 flex items-center justify-between text-[10px] font-mono text-slate-400">
                <span>POLL INTERVAL: <strong className="text-slate-200">800ms</strong></span>
                <span>SIGKILL REFLEX: <strong className="text-emerald-400">AUTONOMOUS</strong></span>
              </div>
            </div>

            {/* Node 3: OUTPOST-PI-7INCH (The User's Exact Hardware Setup!) */}
            <div className="p-3.5 rounded-lg bg-gradient-to-br from-[#0a101b]/90 to-[#121c2e]/90 border border-emerald-500/35 hover:border-emerald-500/60 transition-all shadow-[0_0_15px_rgba(0,255,136,0.06)]">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2.5">
                  <div className="p-1.5 rounded bg-emerald-950/60 border border-emerald-500/30 text-emerald-400">
                    <Cpu className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="text-xs font-bold font-mono text-white flex items-center gap-2">
                      <span>OUTPOST-PI-7INCH</span>
                      <span className="text-[9px] px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                        ROVER / KIOSK
                      </span>
                    </div>
                    <div className="text-[10px] font-mono text-slate-400">Raspberry Pi 4 • 7" DSI Touchscreen (800×480)</div>
                  </div>
                </div>
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
              </div>

              <div className="mt-3 flex items-center justify-between text-[10px] font-mono bg-slate-950/60 p-2 rounded border border-emerald-500/20">
                <span className="text-slate-400">BUS: <strong className="text-emerald-400">DSI ACTIVE</strong></span>
                <span className="text-slate-400">TEMP: <strong className="text-cyan-400">41.8°C</strong></span>
                <span className="text-slate-400">SPEAKER: <strong className="text-emerald-400">ONLINE</strong></span>
              </div>

              <div className="mt-2.5 flex items-center justify-end">
                <button
                  onClick={() => {
                    tacticalAudio.playRadarPing();
                    setDirectiveStatus('7-INCH DSI TOUCH TEST: Capacitive grid calibration verified (100% OK)');
                  }}
                  className="px-2.5 py-1 rounded bg-emerald-950/40 hover:bg-emerald-900/60 border border-emerald-500/40 text-emerald-300 text-[10px] font-mono font-semibold uppercase tracking-wider cursor-pointer"
                >
                  TOUCH TEST
                </button>
              </div>
            </div>

          </div>

          {/* Right Column (7 Cols): Kinetic Interception Log // Real-Time Feed */}
          <div className="lg:col-span-7 flex flex-col h-[460px] rounded-xl bg-[#080d16]/95 border border-cyan-500/30 shadow-[0_0_20px_rgba(0,229,255,0.05)] overflow-hidden">
            
            {/* Terminal Top Bar */}
            <div className="flex items-center justify-between px-4 py-2.5 bg-[#0d1522] border-b border-cyan-500/20 font-mono text-xs">
              <div className="flex items-center gap-2 text-cyan-400 font-bold tracking-wider">
                <TerminalIcon className="w-4 h-4" />
                <span>KINETIC INTERCEPTION LOG // REAL-TIME FEED</span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setStreamActive(!streamActive)}
                  title={streamActive ? 'Pause log stream' : 'Resume log stream'}
                  className="p-1 rounded text-slate-400 hover:text-cyan-300 hover:bg-slate-800 transition-colors"
                >
                  {streamActive ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
                </button>
                <button
                  onClick={() => setLogs([])}
                  title="Clear log"
                  className="p-1 rounded text-slate-400 hover:text-rose-400 hover:bg-slate-800 transition-colors"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              </div>
            </div>

            {/* Terminal Stream Content */}
            <div
              ref={logContainerRef}
              className="flex-1 p-3.5 overflow-y-auto font-mono text-[11px] space-y-2 select-text"
            >
              {logs.length === 0 ? (
                <div className="text-slate-500 italic text-center py-12">Log buffer cleared. Listening for incoming telemetry...</div>
              ) : (
                logs.map((entry) => {
                  const badgeStyle = {
                    DETECT: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
                    DEFEND: 'bg-cyan-500/20 text-cyan-400 border-cyan-500/30',
                    DEFEAT: 'bg-rose-500/20 text-rose-300 border-rose-500/40 font-bold animate-pulse',
                    DEFCON: 'bg-amber-500/20 text-amber-300 border-amber-500/40 font-bold',
                    SYSTEM: 'bg-slate-800 text-slate-300 border-slate-700',
                  }[entry.category];

                  return (
                    <div key={entry.id} className="flex items-start gap-2.5 leading-relaxed hover:bg-white/[0.02] p-1 rounded transition-colors">
                      <span className="text-slate-500 text-[10px] shrink-0 pt-0.5">{entry.timestamp}</span>
                      <span className={`px-1.5 py-0.2 rounded border text-[9px] uppercase tracking-wider shrink-0 ${badgeStyle}`}>
                        {entry.category}
                      </span>
                      <span className="text-slate-300 break-words flex-1">
                        {entry.message}
                      </span>
                      {entry.latency && (
                        <span className="text-[10px] text-emerald-400/80 shrink-0 font-bold">
                          {entry.latency}
                        </span>
                      )}
                    </div>
                  );
                })
              )}
            </div>

            {/* Terminal Footer status bar */}
            <div className="px-4 py-2 bg-[#0a101b] border-t border-cyan-500/15 flex items-center justify-between text-[10px] font-mono text-slate-400">
              <div className="flex items-center gap-2">
                <span className="text-cyan-400 font-bold">STREAM:</span>
                <span>/dev/astra_sentry_stream --follow</span>
              </div>
              <div className="text-slate-500">
                BUFFER: {logs.length} / 50 EVENTS
              </div>
            </div>

          </div>

        </div>

      </main>

      {/* ── 5. COMMANDER WAR ROOM FOOTER STRIP ───────────────────────────────── */}
      <footer className="sticky bottom-0 z-30 border-t border-cyan-500/30 bg-[#06090e]/95 backdrop-blur-xl px-4 lg:px-8 py-3 shadow-[0_-5px_25px_rgba(0,0,0,0.7)]">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-3">
          
          {/* Directive Status Readout */}
          <div className="flex items-center gap-2 text-xs font-mono text-slate-300 w-full md:w-auto">
            <span className="w-2 h-2 rounded-full bg-cyan-400 animate-ping shrink-0" />
            <span className="truncate text-cyan-300 font-semibold">{directiveStatus}</span>
          </div>

          {/* Action Buttons */}
          <div className="flex flex-wrap items-center gap-2 w-full md:w-auto justify-end">
            
            {/* Action 1: Simulate Threat & Auto-Defeat */}
            <button
              onClick={handleSimulateDefeat}
              className={`px-3.5 py-2 rounded bg-gradient-to-r from-rose-600 to-rose-700 hover:from-rose-500 hover:to-rose-600 text-white font-mono text-xs font-bold uppercase tracking-wider shadow-[0_0_18px_rgba(255,23,68,0.4)] transition-all transform active:scale-95 flex items-center gap-2 cursor-pointer ${
                isKioskMode ? 'text-sm py-3 px-5' : ''
              }`}
            >
              <Zap className="w-4 h-4 fill-current" />
              <span>SIMULATE THREAT & AUTO-DEFEAT</span>
            </button>

            {/* Action 2: Fleet Lockdown */}
            <button
              onClick={handleToggleLockdown}
              className={`px-3.5 py-2 rounded font-mono text-xs font-bold uppercase tracking-wider transition-all transform active:scale-95 flex items-center gap-2 cursor-pointer ${
                isLockdown
                  ? 'bg-rose-500 text-white shadow-[0_0_15px_rgba(255,23,68,0.6)] animate-pulse'
                  : 'bg-slate-900 hover:bg-slate-800 border border-cyan-500/40 text-cyan-300'
              } ${isKioskMode ? 'text-sm py-3 px-5' : ''}`}
            >
              <Lock className="w-4 h-4" />
              <span>{isLockdown ? 'LOCKDOWN ACTIVE' : 'FLEET LOCKDOWN'}</span>
            </button>

            {/* Action 3: 7" Touch Kiosk Mode Toggle */}
            <button
              onClick={() => {
                tacticalAudio.playClick();
                setIsKioskMode(!isKioskMode);
              }}
              title="Toggle touch-friendly large button layout for 7-inch displays"
              className={`p-2 rounded font-mono text-xs border transition-colors ${
                isKioskMode 
                  ? 'bg-emerald-500/20 border-emerald-400 text-emerald-300' 
                  : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
              }`}
            >
              <Maximize2 className="w-4 h-4" />
            </button>

          </div>

        </div>
      </footer>

    </div>
  );
}
