import React, { useState, useEffect } from 'react';
import { ShieldAlert, Search, Filter, AlertTriangle, Trash2 } from 'lucide-react';
import { Card, Input, Button, Badge, Table, TableHeader, TableRow, TableHead, TableBody, TableCell, PageContainer, PageSection, PageHeader } from '@/components/ui';
import { useWebSocket } from '@/providers/WebSocketProvider';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import api from '@/lib/api';
import { motion, AnimatePresence } from 'framer-motion';
import { format } from 'date-fns';
import { toast } from 'sonner';
import { useScopedDevice } from '@/contexts/ScopedDeviceContext';
import { formatIncidentTime, formatRelativeTime } from '@/utils/timeUtils';

export function Threats() {
  const [searchTerm, setSearchTerm] = useState('');
  const { subscribe } = useWebSocket();
  const queryClient = useQueryClient();
  const { scopedDeviceId } = useScopedDevice();
  const [threats, setThreats] = useState<any[]>([]);
  const [isClearing, setIsClearing] = useState(false);

  const { data: initialThreats, isLoading, isError } = useQuery({
    queryKey: ['threats'],
    queryFn: async () => {
      const res = await api.get('/threats');
      return res.data;
    },
    refetchInterval: 5000
  });

  useEffect(() => {
    if (initialThreats) {
      setThreats(initialThreats);
    }
  }, [initialThreats]);

  useEffect(() => {
    // Listen for timeline events from Attacks Engine
    const unsubscribe = subscribe('timeline', (payload) => {
      if (payload.event === 'NEW_INCIDENT') {
        setThreats(prev => [payload.incident, ...prev].slice(0, 50));
      }
    });

    return () => unsubscribe();
  }, [subscribe]);

  const handleClearAllThreats = async () => {
    setIsClearing(true);
    try {
      await api.post('/threats/clear-all');
      setThreats([]);
      queryClient.invalidateQueries({ queryKey: ['threats'] });
      queryClient.invalidateQueries({ queryKey: ['threats-history'] });
      toast.success('All incident logs & queue successfully cleared!');
    } catch (err) {
      console.error('Failed to clear incident logs', err);
      toast.error('Failed to clear incident logs');
    } finally {
      setIsClearing(false);
    }
  };

  const filteredThreats = threats.filter(t => {
    const matchesSearch = t.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          t.type.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          t.severity.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesDevice = scopedDeviceId ? (t.target && t.target.includes(scopedDeviceId)) : true;
    return matchesSearch && matchesDevice;
  });

  return (
    <PageContainer>
      <PageHeader 
        title="Incident Logs" 
        description="Analyze and manage live security incidents across the agency network."
      >
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={handleClearAllThreats}
            disabled={isClearing || threats.length === 0}
            className="border-red-500/40 text-red-400 hover:bg-red-500/10 text-xs font-mono flex items-center gap-1.5"
          >
            <Trash2 size={13} /> {isClearing ? 'Clearing Logs...' : 'Clear All Incident Logs'}
          </Button>
        </div>
      </PageHeader>

      <PageSection>
        <Card>
          <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex w-full max-w-sm items-center gap-2">
              <Input 
                icon={<Search size={16} />}
                placeholder="Search threats..." 
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
            <div className="flex items-center gap-2">
              <Button 
                variant="outline" 
                size="sm" 
                onClick={handleClearAllThreats}
                disabled={isClearing || threats.length === 0}
                className="border-red-500/30 text-red-400 hover:bg-red-500/10"
              >
                <Trash2 size={14} className="mr-1.5" />
                Clear Logs
              </Button>
              <Button variant="outline" size="sm">
                <Filter size={16} className="mr-2" />
                Filters
              </Button>
              <Button variant="primary" size="sm">Export Report</Button>
            </div>
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="font-mono uppercase tracking-widest">Threat ID</TableHead>
                <TableHead className="font-mono uppercase tracking-widest">Name / Type</TableHead>
                <TableHead className="font-mono uppercase tracking-widest">Severity</TableHead>
                <TableHead className="font-mono uppercase tracking-widest">Status</TableHead>
                <TableHead className="font-mono uppercase tracking-widest">Target</TableHead>
                <TableHead className="text-right font-mono uppercase tracking-widest">Timestamp</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <AnimatePresence>
                {isLoading ? (
                  <motion.tr initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                    <TableCell colSpan={6} className="text-center py-12">
                      <div className="flex flex-col items-center justify-center space-y-4 text-primary animate-pulse">
                        <div className="flex items-center space-x-2">
                          <div className="w-2 h-2 rounded-full bg-primary animate-ping" />
                          <div className="w-2 h-2 rounded-full bg-primary animate-ping delay-75" />
                          <div className="w-2 h-2 rounded-full bg-primary animate-ping delay-150" />
                        </div>
                        <span className="font-mono uppercase tracking-widest text-sm text-glow">Scanning Network...</span>
                      </div>
                    </TableCell>
                  </motion.tr>
                ) : isError ? (
                  <motion.tr initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                    <TableCell colSpan={6} className="text-center py-8 text-danger font-mono uppercase tracking-widest bg-danger/5">
                      <AlertTriangle className="inline-block mr-2 w-5 h-5" />
                      Failed to establish connection with threat intelligence server.
                    </TableCell>
                  </motion.tr>
                ) : filteredThreats.length === 0 ? (
                  <motion.tr initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                    <TableCell colSpan={6} className="text-center py-8 text-white/50 font-mono">
                      No active threats recorded. System is secure.
                    </TableCell>
                  </motion.tr>
                ) : (
                  filteredThreats.map((threat) => (
                    <motion.tr 
                      key={threat.id} 
                      className="border-b border-border-color transition-colors hover:bg-white/5 cursor-pointer"
                      initial={{ opacity: 0, y: -20, backgroundColor: 'rgba(255,42,109,0.3)' }}
                      animate={{ opacity: 1, y: 0, backgroundColor: 'rgba(0,0,0,0)' }}
                      transition={{ duration: 0.5 }}
                    >
                      <TableCell className="font-medium font-mono text-primary">{threat.id.split('-').slice(0, 3).join('-')}</TableCell>
                      <TableCell>
                        <div className="flex flex-col">
                          <span className="font-rajdhani font-bold text-white tracking-wide">{threat.name}</span>
                          <span className="text-xs text-white/50 font-mono">{threat.type}</span>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant={
                          threat.severity === 'CRITICAL' ? 'danger' :
                          threat.severity === 'HIGH' ? 'warning' :
                          threat.severity === 'MEDIUM' ? 'info' : 'default'
                        }>
                          {threat.severity === 'CRITICAL' && <AlertTriangle size={12} className="mr-1 inline-block" />}
                          {threat.severity}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <span className={`text-sm font-mono font-bold ${threat.status === 'BLOCKED' ? 'text-success' : 'text-danger animate-pulse'}`}>
                          {threat.status}
                        </span>
                      </TableCell>
                      <TableCell className="text-white/70">
                        <div className="flex flex-col gap-1 items-start">
                          {threat.target?.includes('SCRIPTED') ? (
                            <Badge variant="outline" className="text-primary border-primary/50 text-[10px] px-1 py-0 h-4">SCRIPTED</Badge>
                          ) : (
                            <Badge variant="outline" className="text-success border-success/50 text-[10px] px-1 py-0 h-4">HARDWARE</Badge>
                          )}
                          <span className="truncate max-w-[150px] text-xs font-mono" title={threat.target}>{threat.target}</span>
                        </div>
                      </TableCell>
                      <TableCell className="text-right text-primary/70 font-mono text-xs">
                        <div className="flex flex-col items-end">
                          <span>{formatIncidentTime(threat.createdAt)}</span>
                          <span className="text-[10px] text-white/40">{formatRelativeTime(threat.createdAt)}</span>
                        </div>
                      </TableCell>
                    </motion.tr>
                  ))
                )}
              </AnimatePresence>
            </TableBody>
          </Table>
        </Card>
      </PageSection>
    </PageContainer>
  );
}
