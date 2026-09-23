import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Bell, Moon, Sun, Globe, Bot, Maximize, Minimize } from 'lucide-react';
import { Input, Avatar, Tooltip } from '@/components/ui';
import { useTheme } from '@/providers/theme-provider';
import { useAssistant } from '@/providers/AssistantProvider';
import { useWebSocket } from '@/providers/WebSocketProvider';
import { motion } from 'framer-motion';

export function Header() {
  const navigate = useNavigate();
  const { theme, setTheme } = useTheme();
  const { toggleAssistant, isAssistantOpen } = useAssistant();
  const { isConnected } = useWebSocket();
  const [isFullscreen, setIsFullscreen] = useState(false);

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {});
      setIsFullscreen(true);
    } else {
      if (document.exitFullscreen) {
        document.exitFullscreen().catch(() => {});
      }
      setIsFullscreen(false);
    }
  };

  useEffect(() => {
    const handleFsChange = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };
    document.addEventListener('fullscreenchange', handleFsChange);
    return () => document.removeEventListener('fullscreenchange', handleFsChange);
  }, []);

  return (
    <header className="sticky top-0 z-50 flex h-16 w-full items-center justify-between glass-panel !overflow-visible border-b border-x-0 border-t-0 px-6 backdrop-blur-xl">
      <div className="flex items-center gap-4 w-1/3">
        <motion.div 
          className="w-full relative group"
          whileFocus="focus"
          whileHover="hover"
        >
          <div className="absolute inset-0 bg-primary/20 blur-xl opacity-0 group-hover:opacity-100 transition-opacity duration-500 rounded-full" />
          <Input
            icon={<Search size={16} className="text-white/50 group-hover:text-primary transition-colors" />}
            placeholder="Search endpoints, threats, alerts..."
            className="w-full max-w-md bg-surface/50 border-white/5 focus:border-primary/50 focus:bg-surface/80 transition-all duration-300 relative z-10 hover:border-primary/30"
          />
        </motion.div>
      </div>

      <div className="flex items-center gap-3">
        {/* Live C2 Backend Connection Status Badge */}
        <div className={`flex items-center gap-2 px-2.5 py-1 rounded-full text-xs font-mono font-semibold border transition-all ${
          isConnected
            ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30 shadow-[0_0_10px_rgba(16,185,129,0.2)]'
            : 'bg-amber-500/10 text-amber-300 border-amber-500/30 animate-pulse shadow-[0_0_12px_rgba(245,158,11,0.25)]'
        }`}>
          <span className={`h-2 w-2 rounded-full ${isConnected ? 'bg-emerald-400 shadow-[0_0_8px_rgba(16,185,129,0.8)]' : 'bg-amber-400 animate-ping'}`} />
          <span className="hidden sm:inline">{isConnected ? 'C2 ACTIVE' : 'WAKING UP C2...'}</span>
          <span className="sm:hidden">{isConnected ? 'C2' : 'SYNC'}</span>
        </div>

        {/* On-Screen Fullscreen / Kiosk Toggle for Touchscreen */}
        <Tooltip content={isFullscreen ? "Exit Fullscreen (Kiosk)" : "Enter Fullscreen (Kiosk)"}>
          <motion.button 
            whileHover={{ scale: 1.1, backgroundColor: "rgba(255,255,255,0.1)" }}
            whileTap={{ scale: 0.95 }}
            onClick={toggleFullscreen}
            className="rounded-full p-2 text-white/70 hover:text-white transition-colors border border-transparent hover:border-white/20"
          >
            {isFullscreen ? <Minimize size={18} /> : <Maximize size={18} />}
          </motion.button>
        </Tooltip>

        {/* Manual World Threat Map Button */}
        <Tooltip content="Launch World Threat Map War Room">
          <motion.button 
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            onClick={(e) => {
              e.stopPropagation();
              window.dispatchEvent(new CustomEvent('trigger-idle-screensaver'));
            }}
            className="flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-mono font-bold bg-primary/20 text-primary border border-primary/40 hover:bg-primary/30 transition-all shadow-[0_0_15px_rgba(5,217,232,0.25)]"
          >
            <Globe size={15} className="animate-spin text-primary" style={{ animationDuration: '12s' }} />
            <span>WORLD THREAT MAP</span>
          </motion.button>
        </Tooltip>
        <div className="text-sm text-white/50 hidden md:block">
          {new Date().toLocaleTimeString(navigator.language, {
            hour: '2-digit',
            minute: '2-digit',
          })}
        </div>
        
        <Tooltip content="ASTRA AI Assistant">
          <motion.button 
            whileHover={{ scale: 1.1, backgroundColor: "rgba(0,255,65,0.1)" }}
            whileTap={{ scale: 0.95 }}
            onClick={toggleAssistant}
            className={`rounded-full p-2 transition-colors border ${
              isAssistantOpen 
                ? 'text-primary border-primary bg-primary/10 shadow-[0_0_15px_rgba(0,255,65,0.3)]' 
                : 'text-white/70 hover:text-primary border-transparent hover:border-primary/50'
            }`}
          >
            <Bot size={20} />
          </motion.button>
        </Tooltip>

        <Tooltip content="Notifications & Threat Radar">
          <motion.button 
            whileHover={{ scale: 1.1, backgroundColor: "rgba(255,255,255,0.1)" }}
            whileTap={{ scale: 0.95 }}
            onClick={() => navigate('/threats')}
            className="relative rounded-full p-2 text-white/70 hover:text-white transition-colors border border-transparent hover:border-white/20 cursor-pointer"
          >
            <Bell size={20} />
            <span className="absolute right-1 top-1 h-2 w-2 rounded-full bg-danger animate-pulse shadow-[0_0_8px_rgba(255,61,113,0.8)]" />
          </motion.button>
        </Tooltip>

        <Tooltip content="Toggle Theme">
          <motion.button
            whileHover={{ scale: 1.1, backgroundColor: "rgba(255,255,255,0.1)" }}
            whileTap={{ scale: 0.95 }}
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            className="rounded-full p-2 text-white/70 hover:text-white transition-colors border border-transparent hover:border-white/20 cursor-pointer"
          >
            {theme === 'dark' ? <Moon size={20} /> : <Sun size={20} />}
          </motion.button>
        </Tooltip>

        <div className="h-6 w-px bg-white/10 mx-2 shadow-[0_0_10px_rgba(255,255,255,0.2)]" />

        <Tooltip content="Profile & System Settings">
          <motion.button 
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            onClick={() => navigate('/settings')}
            className="flex items-center gap-2 rounded-full hover:ring-2 hover:ring-primary/50 transition-all outline-none shadow-[0_0_15px_rgba(5,217,232,0.1)] hover:shadow-[0_0_20px_rgba(5,217,232,0.3)] cursor-pointer"
          >
            <Avatar fallback="AX" size="sm" />
          </motion.button>
        </Tooltip>
      </div>
    </header>
  );
}
