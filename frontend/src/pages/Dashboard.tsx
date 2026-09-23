import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence, Variants } from 'framer-motion';
import { 
  ShieldAlert, Server, Activity, 
  Cpu, HardDrive, Network 
} from 'lucide-react';
import { StatCard, Card, Badge, Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from '@/components/ui';
import { useWebSocket } from '@/providers/WebSocketProvider';
import { toast } from 'sonner';
import { useQuery } from '@tanstack/react-query';
import api from '@/lib/api';
import { useScopedDevice } from '@/contexts/ScopedDeviceContext';
import { formatIncidentTime } from '@/utils/timeUtils';

export function Dashboard() {
  const { subscribe, isConnected } = useWebSocket();
  const { scopedDeviceId } = useScopedDevice();
  const [liveThreats, setLiveThreats] = useState<any[]>([]);
  const [recentAlerts, setRecentAlerts] = useState<any[]>([]);
  const [showRedPopup, setShowRedPopup] = useState(false);
  const [popupData, setPopupData] = useState<any>(null);
  
  const { data: metrics } = useQuery({
    queryKey: ['dashboard-metrics'],
    queryFn: async () => {
      const res = await api.get('/dashboard');
      return res.data;
    },
    refetchInterval: 5000
  });

  const { data: initialThreats } = useQuery({
    queryKey: ['active-threats'],
    queryFn: async () => {
      const res = await api.get('/threats');
      return res.data;
    },
    refetchInterval: 5000
  });

  useEffect(() => {
    if (initialThreats) {
      setLiveThreats(initialThreats);
      
      let filtered = initialThreats;
      if (scopedDeviceId) {
        filtered = filtered.filter((t: any) => t.target && t.target.includes(scopedDeviceId));
      }
      setRecentAlerts(filtered.slice(0, 10).map((t: any) => ({
        id: t.id,
        type: t.type,
        severity: t.severity,
        source: t.target,
        time: formatIncidentTime(t.createdAt)
      })));
    }
  }, [initialThreats, scopedDeviceId]);
  
  const [liveMetrics, setLiveMetrics] = useState<{cpuUsage?: number, ramUsage?: number} | null>(null);

  useEffect(() => {
    const unsubscribeTimeline = subscribe('timeline', (payload) => {
      if (payload.event === 'NEW_INCIDENT') {
        const incident = payload.incident;
        
        setLiveThreats(prev => {
          if (prev.some(t => t.id === incident.id)) return prev;
          return [incident, ...prev];
        });

        if (scopedDeviceId && incident.target && !incident.target.includes(scopedDeviceId)) {
          return; 
        }

        setRecentAlerts(prev => [
          {
            id: incident.id,
            type: incident.type,
            severity: incident.severity,
            source: incident.target,
            time: 'Just now'
          },
          ...prev
        ].slice(0, 10)); 
        
        setPopupData(incident);
        setShowRedPopup(true);
        setTimeout(() => setShowRedPopup(false), 4000);
      }
    });

    const unsubscribeTelemetry = subscribe('telemetry', (telemetry: any) => {
      setLiveMetrics({
        cpuUsage: telemetry.cpuUsage,
        ramUsage: telemetry.ramUsage
      });
    });

    return () => {
      unsubscribeTimeline();
      unsubscribeTelemetry();
    };
  }, [subscribe, scopedDeviceId]);

  const containerVariants: Variants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: { staggerChildren: 0.1 }
    }
  };

  const itemVariants: Variants = {
    hidden: { opacity: 0, y: 20 },
    show: { opacity: 1, y: 0, transition: { type: "spring", stiffness: 300, damping: 24 } }
  };

  return (
    <motion.div variants={containerVariants} initial="hidden" animate="show" className="space-y-6 pb-12 relative">
      <AnimatePresence>
        {showRedPopup && popupData && (
          <motion.div
            initial={{ opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.8 }}
            className="fixed inset-0 z-[9999] flex items-center justify-center pointer-events-none"
          >
            <div className="bg-red-600/90 backdrop-blur-md text-white font-mono p-12 border-4 border-red-800 shadow-[0_0_100px_rgba(220,38,38,0.8)] rounded-xl flex flex-col items-center">
              <ShieldAlert size={80} className="mb-4 animate-bounce" />
              <h2 className="text-4xl font-bold mb-2 glitch" data-text="THREAT DETECTED">THREAT DETECTED</h2>
              <p className="text-xl">{popupData.type}</p>
              <p className="text-lg opacity-80 mt-2">Target: {popupData.target}</p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <motion.div variants={itemVariants} className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <motion.div whileHover={{ scale: 1.02 }} className="cursor-default">
          <h1 className="text-3xl font-mono font-bold text-white flex items-center gap-2 uppercase tracking-widest text-glow transition-colors hover:text-primary">
            {scopedDeviceId ? 'Device Overview' : 'System Overview'} <span className="animate-pulse text-primary">_</span>
          </h1>
          <p className="text-white/60 font-mono text-sm tracking-wider uppercase mt-1">Real-time threat monitoring and system health.</p>
        </motion.div>
        <div className="flex items-center gap-2">
          {isConnected ? (
            <motion.div whileHover={{ scale: 1.05 }}>
              <Badge variant="success" className="px-3 py-1 text-sm shadow-[0_0_10px_rgba(0,230,118,0.3)] transition-all hover:shadow-[0_0_20px_rgba(0,230,118,0.6)]">
                <span className="mr-2 h-2 w-2 rounded-none cyber-cut bg-success animate-pulse inline-block" />
                System Secure (Live)
              </Badge>
            </motion.div>
          ) : (
            <Badge variant="danger" className="px-3 py-1 text-sm shadow-[0_0_10px_rgba(255,42,109,0.3)]">
              <span className="mr-2 h-2 w-2 rounded-none cyber-cut bg-danger inline-block" />
              Disconnected
            </Badge>
          )}
        </div>
      </motion.div>

      <motion.div variants={itemVariants} className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
        <motion.div whileHover={{ scale: 1.02, y: -5 }} transition={{ type: "spring", stiffness: 300 }}>
          <StatCard 
            title="Active Threats" 
            value={scopedDeviceId 
              ? (liveThreats.filter((t: any) => t.target && t.target.includes(scopedDeviceId)).length).toString() 
              : (liveThreats.length).toString()} 
            icon={<ShieldAlert />} 
            trend={{ value: 0, label: 'vs last 24h', isPositive: false }} 
          />
        </motion.div>
        <motion.div whileHover={{ scale: 1.02, y: -5 }} transition={{ type: "spring", stiffness: 300 }}>
          <StatCard 
            title="Protected Devices" 
            value={scopedDeviceId ? "1" : (metrics?.connectedDevices?.toString() || "0")} 
            icon={<Server />} 
            trend={{ value: 0, label: 'vs last 24h', isPositive: true }} 
          />
        </motion.div>
        <motion.div whileHover={{ scale: 1.02, y: -5 }} transition={{ type: "spring", stiffness: 300 }}>
          <StatCard 
            title="System Health" 
            value={`${Math.max(0, 100 - (scopedDeviceId ? liveThreats.filter((t: any) => t.target && t.target.includes(scopedDeviceId)).length : liveThreats.length) * 10)}%`} 
            icon={<Activity />} 
            trend={{ value: 0, label: 'vs last 24h', isPositive: true }} 
          />
        </motion.div>
        <motion.div whileHover={{ scale: 1.02, y: -5 }} transition={{ type: "spring", stiffness: 300 }}>
          <StatCard 
            title="Network Traffic" 
            value="0.0 TB" 
            icon={<Network />} 
            trend={{ value: 0, label: 'vs last 24h', isPositive: true }} 
          />
        </motion.div>
      </motion.div>

      {/* Map Removed as requested */}

      <motion.div variants={itemVariants} className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* System Health Card */}
        <motion.div whileHover={{ scale: 1.02 }} transition={{ type: "spring", stiffness: 400 }} className="h-full">
          <Card className="flex flex-col relative overflow-hidden group h-full shadow-lg hover:shadow-[0_0_25px_rgba(5,217,232,0.15)] transition-shadow duration-300">
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-primary/5 to-transparent -translate-x-[100%] group-hover:translate-x-[100%] transition-transform duration-1000 ease-in-out pointer-events-none" />
            <h3 className="mb-4 text-lg font-mono font-bold tracking-widest uppercase text-white flex items-center">
              <span className="text-primary mr-2 animate-pulse">{'>'}</span> System Health
            </h3>
            <div className="space-y-8 flex-1 relative z-10 mt-2">
              <div className="group/bar">
                <div className="mb-2 flex items-center justify-between text-sm font-mono uppercase tracking-wider">
                  <span className="flex items-center gap-2 text-white/70 group-hover/bar:text-white transition-colors"><Cpu size={16} className="text-primary"/> CPU Usage</span>
                  <span className="font-bold text-white text-glow">{liveMetrics?.cpuUsage ?? metrics?.cpuUsage ?? 18}%</span>
                </div>
                <div className="h-2 w-full overflow-hidden rounded-none bg-white/10 cyber-cut">
                  <motion.div initial={{ width: 0 }} animate={{ width: `${liveMetrics?.cpuUsage ?? metrics?.cpuUsage ?? 18}%` }} transition={{ duration: 0.5 }} className="h-full bg-primary shadow-[0_0_10px_rgba(5,217,232,0.8)]" />
                </div>
              </div>
              
              <div className="group/bar">
                <div className="mb-2 flex items-center justify-between text-sm font-mono uppercase tracking-wider">
                  <span className="flex items-center gap-2 text-white/70 group-hover/bar:text-white transition-colors"><Server size={16} className="text-warning"/> Memory (RAM)</span>
                  <span className="font-bold text-white text-glow">{liveMetrics?.ramUsage ?? metrics?.ramUsage ?? 45}%</span>
                </div>
                <div className="h-2 w-full overflow-hidden rounded-none bg-white/10 cyber-cut">
                  <motion.div initial={{ width: 0 }} animate={{ width: `${liveMetrics?.ramUsage ?? metrics?.ramUsage ?? 45}%` }} transition={{ duration: 0.5 }} className="h-full bg-warning shadow-[0_0_10px_rgba(243,230,0,0.8)]" />
                </div>
              </div>

              <div className="group/bar">
                <div className="mb-2 flex items-center justify-between text-sm font-mono uppercase tracking-wider">
                  <span className="flex items-center gap-2 text-white/70 group-hover/bar:text-white transition-colors"><HardDrive size={16} className="text-danger"/> Disk Storage</span>
                  <span className="font-bold text-white text-glow">{metrics?.storage ?? 32}%</span>
                </div>
                <div className="h-2 w-full overflow-hidden rounded-none bg-white/10 cyber-cut">
                  <motion.div initial={{ width: 0 }} animate={{ width: `${metrics?.storage ?? 32}%` }} transition={{ duration: 1, delay: 0.9 }} className="h-full bg-danger shadow-[0_0_10px_rgba(255,42,109,0.8)]" />
                </div>
              </div>
            </div>
          </Card>
        </motion.div>

        {/* Security Modules Card */}
        <motion.div whileHover={{ scale: 1.02 }} transition={{ type: "spring", stiffness: 400 }} className="h-full">
          <Card className="flex flex-col relative overflow-hidden group h-full shadow-lg hover:shadow-[0_0_25px_rgba(5,217,232,0.15)] transition-shadow duration-300">
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-primary/5 to-transparent -translate-x-[100%] group-hover:translate-x-[100%] transition-transform duration-1000 ease-in-out pointer-events-none" />
            <h3 className="mb-4 text-lg font-mono font-bold tracking-widest uppercase text-white flex items-center">
              <span className="text-primary mr-2 animate-pulse">{'>'}</span> Active Defense Grid
            </h3>
            <div className="space-y-6 flex-1 relative z-10 mt-2">
              {[
                { name: 'AI Heuristics Engine', status: isConnected, icon: <Activity size={16} /> },
                { name: 'Endpoint Firewall', status: isConnected, icon: <ShieldAlert size={16} /> },
                { name: 'Network Telemetry', status: isConnected, icon: <Network size={16} /> }
              ].map((module, idx) => (
                <div key={idx} className="flex items-center justify-between p-3 bg-white/5 border border-white/10 group-hover:border-primary/20 transition-colors">
                  <div className="flex items-center gap-3">
                    <span className={module.status ? "text-primary" : "text-danger"}>{module.icon}</span>
                    <span className="font-mono text-sm tracking-widest uppercase text-white/80">{module.name}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`h-2 w-2 rounded-full ${module.status ? "bg-success animate-pulse" : "bg-danger"}`} />
                    <span className={`font-mono text-xs font-bold ${module.status ? "text-success text-glow" : "text-danger"}`}>
                      {module.status ? 'ONLINE' : 'OFFLINE'}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </motion.div>

        {/* Threat Distribution Card */}
        <motion.div whileHover={{ scale: 1.02 }} transition={{ type: "spring", stiffness: 400 }} className="h-full">
          <Card className="flex flex-col relative overflow-hidden group h-full shadow-lg hover:shadow-[0_0_25px_rgba(5,217,232,0.15)] transition-shadow duration-300">
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-primary/5 to-transparent -translate-x-[100%] group-hover:translate-x-[100%] transition-transform duration-1000 ease-in-out pointer-events-none" />
            <h3 className="mb-4 text-lg font-mono font-bold tracking-widest uppercase text-white flex items-center">
              <span className="text-primary mr-2 animate-pulse">{'>'}</span> Risk Posture
            </h3>
            <div className="space-y-6 flex-1 relative z-10 mt-2">
              {[
                { label: 'Critical', color: 'bg-danger text-danger', count: liveThreats.filter(t => t.severity?.toUpperCase() === 'CRITICAL').length },
                { label: 'High', color: 'bg-warning text-warning', count: liveThreats.filter(t => t.severity?.toUpperCase() === 'HIGH').length },
                { label: 'Medium', color: 'bg-primary text-primary', count: liveThreats.filter(t => t.severity?.toUpperCase() === 'MEDIUM').length }
              ].map((severity, idx) => (
                <div key={idx} className="group/bar">
                  <div className="mb-2 flex items-center justify-between text-sm font-mono uppercase tracking-wider">
                    <span className="text-white/70 group-hover/bar:text-white transition-colors">{severity.label}</span>
                    <span className={`font-bold ${severity.color.split(' ')[1]}`}>{severity.count}</span>
                  </div>
                  <div className="h-2 w-full overflow-hidden rounded-none bg-white/10 cyber-cut">
                    <motion.div 
                      initial={{ width: 0 }} 
                      animate={{ width: `${Math.min(100, (severity.count / Math.max(1, liveThreats.length)) * 100)}%` }} 
                      transition={{ duration: 0.5, delay: idx * 0.1 }} 
                      className={`h-full ${severity.color.split(' ')[0]} shadow-[0_0_10px_currentColor]`} 
                    />
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </motion.div>
      </motion.div>

      <motion.div variants={itemVariants}>
        <Card className="shadow-lg hover:shadow-[0_0_25px_rgba(5,217,232,0.1)] transition-shadow duration-300">
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-lg font-mono font-bold tracking-widest uppercase text-white flex items-center">
              <span className="text-primary mr-2 animate-pulse">{'>'}</span> Recent Alerts
            </h3>
            <motion.button whileHover={{ scale: 1.05 }} className="text-xs font-mono uppercase tracking-widest text-primary hover:text-white transition-colors">View All</motion.button>
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="font-mono uppercase tracking-widest">Alert ID</TableHead>
                <TableHead className="font-mono uppercase tracking-widest">Type</TableHead>
                <TableHead className="font-mono uppercase tracking-widest">Severity</TableHead>
                <TableHead className="font-mono uppercase tracking-widest">Source</TableHead>
                <TableHead className="text-right font-mono uppercase tracking-widest">Time</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <AnimatePresence>
                {recentAlerts.map((alert) => (
                  <motion.tr 
                    key={alert.id}
                    initial={{ opacity: 0, y: -20, backgroundColor: "rgba(255,42,109,0.3)" }}
                    animate={{ opacity: 1, y: 0, backgroundColor: "rgba(0,0,0,0)" }}
                    exit={{ opacity: 0, scale: 0.9 }}
                    whileHover={{ scale: 1.01, backgroundColor: "rgba(5, 217, 232, 0.05)" }}
                    transition={{ duration: 0.3 }}
                    className="border-b border-border-color cursor-pointer"
                  >
                    <TableCell className="font-mono font-medium text-white/90">{alert.id}</TableCell>
                    <TableCell className="font-rajdhani font-bold tracking-wide">{alert.type}</TableCell>
                    <TableCell>
                      <Badge variant={
                        alert.severity === 'Critical' ? 'danger' :
                        alert.severity === 'High' ? 'warning' :
                        alert.severity === 'Medium' ? 'info' : 'default'
                      }>
                        {alert.severity}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-white/70 font-mono text-xs">{alert.source}</TableCell>
                    <TableCell className="text-right text-primary/70 font-mono text-xs">{alert.time}</TableCell>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </TableBody>
          </Table>
        </Card>
      </motion.div>
    </motion.div>
  );
}
