import { useEffect, useState } from 'react';
import api from '../api/client';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '@/components/ui/select';
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';

function PostRow({ p }) {
  return (
    <Card>
      <CardContent className="space-y-1 py-4">
        <p className="font-medium">{p.title}</p>
        <p className="text-sm text-muted-foreground">
          by {p.authorName} · {p.createdDate}
        </p>
        <div className="flex gap-2">
          <Badge variant="secondary">{p.upvoteCount} upvotes</Badge>
          <Badge variant="secondary">{p.commentCount} comments</Badge>
        </div>
      </CardContent>
    </Card>
  );
}

export default function Forum() {
  const [categories, setCategories] = useState([]);
  const [postsByCategory, setPostsByCategory] = useState({});
  const [activeCategoryId, setActiveCategoryId] = useState(null);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [searchResults, setSearchResults] = useState([]);

  useEffect(() => {
    api.get('/forum/categories').then((res) => {
      setCategories(res.data);
      if (res.data.length > 0) setActiveCategoryId(String(res.data[0].id));
    }).catch(() => {});
  }, []);

  function loadCategoryPosts(categoryId) {
    api.get(`/forum/categories/${categoryId}/posts`).then((res) => {
      setPostsByCategory((prev) => ({ ...prev, [categoryId]: res.data }));
    }).catch(() => {});
  }

  useEffect(() => {
    if (activeCategoryId && !(activeCategoryId in postsByCategory)) {
      loadCategoryPosts(activeCategoryId);
    }
  }, [activeCategoryId]);

  async function runSearch(e) {
    e.preventDefault();
    const keyword = searchInput.trim();
    if (!keyword) return;
    try {
      const res = await api.get('/forum/posts/search', { params: { keyword } });
      setSearchResults(res.data);
      setSearchKeyword(keyword);
    } catch {
      setSearchResults([]);
      setSearchKeyword(keyword);
    }
  }

  function clearSearch() {
    setSearchKeyword('');
    setSearchInput('');
    setSearchResults([]);
  }

  const [newOpen, setNewOpen] = useState(false);
  const [newForm, setNewForm] = useState({ categoryId: '', title: '', content: '' });
  const [newError, setNewError] = useState('');

  async function createPost(e) {
    e.preventDefault();
    setNewError('');
    try {
      await api.post(`/forum/categories/${newForm.categoryId}/posts`, {
        title: newForm.title,
        content: newForm.content,
      });
      setNewOpen(false);
      setNewForm({ categoryId: '', title: '', content: '' });
      if (newForm.categoryId === activeCategoryId) loadCategoryPosts(activeCategoryId);
    } catch (err) {
      setNewError(err.response?.data?.message ?? 'Could not create post');
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Forum</h1>
        <Dialog open={newOpen} onOpenChange={setNewOpen}>
          <DialogTrigger asChild>
            <Button>New Post</Button>
          </DialogTrigger>
          <DialogContent>
            <form onSubmit={createPost} className="space-y-4">
              <DialogHeader>
                <DialogTitle>Create a post</DialogTitle>
              </DialogHeader>
              <div className="space-y-2">
                <Label htmlFor="post-category">Category</Label>
                <Select value={newForm.categoryId} onValueChange={(v) => setNewForm({ ...newForm, categoryId: v })}>
                  <SelectTrigger id="post-category"><SelectValue placeholder="Choose a category" /></SelectTrigger>
                  <SelectContent>
                    {categories.map((c) => (
                      <SelectItem key={c.id} value={String(c.id)}>{c.categoryName}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="post-title">Title</Label>
                <Input
                  id="post-title"
                  value={newForm.title}
                  onChange={(e) => setNewForm({ ...newForm, title: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="post-content">Content</Label>
                <textarea
                  id="post-content"
                  value={newForm.content}
                  onChange={(e) => setNewForm({ ...newForm, content: e.target.value })}
                  rows={4}
                  className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                />
              </div>
              {newError && <p role="alert" className="text-sm text-destructive">{newError}</p>}
              <DialogFooter>
                <Button
                  type="submit"
                  disabled={!newForm.categoryId || !newForm.title || !newForm.content}
                >
                  Post
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      <form onSubmit={runSearch} className="flex gap-2">
        <Input
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder="Search posts..."
        />
        <Button type="submit" variant="outline">Search</Button>
      </form>

      {searchKeyword ? (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-sm text-muted-foreground">Search results for "{searchKeyword}"</p>
            <Button size="sm" variant="ghost" onClick={clearSearch}>Clear search</Button>
          </div>
          {searchResults.map((p) => <PostRow key={p.id} p={p} />)}
          {searchResults.length === 0 && <p className="text-sm text-muted-foreground">No posts found.</p>}
        </div>
      ) : (
        <Tabs value={activeCategoryId ?? undefined} onValueChange={setActiveCategoryId}>
          <TabsList>
            {categories.map((c) => (
              <TabsTrigger key={c.id} value={String(c.id)}>{c.categoryName}</TabsTrigger>
            ))}
          </TabsList>
          {categories.map((c) => (
            <TabsContent key={c.id} value={String(c.id)} className="space-y-3">
              {(postsByCategory[c.id] ?? []).map((p) => <PostRow key={p.id} p={p} />)}
              {(postsByCategory[c.id] ?? []).length === 0 && (
                <p className="text-sm text-muted-foreground">No posts yet in this category.</p>
              )}
            </TabsContent>
          ))}
        </Tabs>
      )}
    </div>
  );
}
