import { useEffect, useState } from 'react';
import api from '../api/client';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '@/components/ui/select';
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';

function SkillColumn({ title, skills, onRemove }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {skills.length === 0 && <p className="text-sm text-muted-foreground">No skills added yet.</p>}
        {skills.map((s) => (
          <div key={s.id} className="flex items-center justify-between rounded-md border p-3">
            <div>
              <p className="font-medium">{s.skillName}</p>
              <div className="mt-1 flex gap-2">
                <Badge variant="secondary">{s.category}</Badge>
                {s.proficiency && <Badge variant="outline">{s.proficiency}</Badge>}
              </div>
            </div>
            <Button variant="ghost" size="sm" onClick={() => onRemove(s.id)}>Remove</Button>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

export default function Skills() {
  const [catalog, setCatalog] = useState([]);
  const [mySkills, setMySkills] = useState([]);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ skillId: '', skillType: 'CAN_TEACH', proficiency: '' });
  const [error, setError] = useState('');
  const [removeError, setRemoveError] = useState('');

  function loadMySkills() {
    api.get('/me/skills').then((res) => setMySkills(res.data)).catch(() => {});
  }

  useEffect(() => {
    api.get('/skills').then((res) => setCatalog(res.data)).catch(() => {});
    loadMySkills();
  }, []);

  async function addSkill(e) {
    e.preventDefault();
    setError('');
    try {
      await api.post('/me/skills', {
        skillId: Number(form.skillId),
        skillType: form.skillType,
        proficiency: form.proficiency || undefined,
      });
      setOpen(false);
      setForm({ skillId: '', skillType: 'CAN_TEACH', proficiency: '' });
      loadMySkills();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not add skill');
    }
  }

  async function removeSkill(id) {
    setRemoveError('');
    try {
      await api.delete(`/me/skills/${id}`);
      loadMySkills();
      setRemoveError('');
    } catch (err) {
      setRemoveError(err.response?.data?.message ?? 'Could not remove skill');
    }
  }

  const teach = mySkills.filter((s) => s.skillType === 'CAN_TEACH');
  const learn = mySkills.filter((s) => s.skillType === 'WANT_TO_LEARN');

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">My Skills</h1>
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button>Add skill</Button>
          </DialogTrigger>
          <DialogContent>
            <form onSubmit={addSkill} className="space-y-4">
              <DialogHeader>
                <DialogTitle>Add a skill</DialogTitle>
                <DialogDescription>Choose a skill from the catalog and set how you know it.</DialogDescription>
              </DialogHeader>
              <div className="space-y-2">
                <Label htmlFor="skill-select">Skill</Label>
                <Select value={form.skillId} onValueChange={(v) => setForm({ ...form, skillId: v })}>
                  <SelectTrigger id="skill-select"><SelectValue placeholder="Choose a skill" /></SelectTrigger>
                  <SelectContent>
                    {catalog.map((s) => (
                      <SelectItem key={s.id} value={String(s.id)}>{s.skillName} ({s.category})</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="type-select">Type</Label>
                <Select value={form.skillType} onValueChange={(v) => setForm({ ...form, skillType: v })}>
                  <SelectTrigger id="type-select"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CAN_TEACH">Can teach</SelectItem>
                    <SelectItem value="WANT_TO_LEARN">Want to learn</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="proficiency">Proficiency</Label>
                <Input
                  id="proficiency"
                  value={form.proficiency}
                  onChange={(e) => setForm({ ...form, proficiency: e.target.value })}
                  placeholder="Beginner / Intermediate / Advanced"
                />
              </div>
              {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
              <DialogFooter>
                <Button type="submit" disabled={!form.skillId}>Add</Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      {removeError && <p role="alert" className="text-sm text-destructive">{removeError}</p>}

      <div className="grid gap-4 sm:grid-cols-2">
        <SkillColumn title="I can teach" skills={teach} onRemove={removeSkill} />
        <SkillColumn title="I want to learn" skills={learn} onRemove={removeSkill} />
      </div>
    </div>
  );
}
